/*
 * Copyright (c) 2001-2017, Zoltan Farkas All Rights Reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * Additionally licensed with:
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spf4j.perf.impl.ms.tsdb;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import gnu.trove.map.hash.THashMap;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.specific.SpecificDatumReader;
import org.spf4j.base.Closeables;
import org.spf4j.base.Pair;
import org.spf4j.base.avro.AvroCloseableIterable;
import org.spf4j.perf.MeasurementStoreQuery;
import org.spf4j.perf.TimeSeriesRecord;
import org.spf4j.tsdb2.TableDefs;
import org.spf4j.tsdb2.avro.Observation;
import org.spf4j.tsdb2.avro.TableDef;

/**
 * @author Zoltan Farkas
 */
@ParametersAreNonnullByDefault
public final class AvroMeasurementStoreReader implements MeasurementStoreQuery {

  private final Path infoFile;

  private final Path[] dataFiles;

  public AvroMeasurementStoreReader(final Path infoFile) throws IOException {
    this(infoFile, lookupObservationFiles(infoFile).toArray(new Path[1]));
  }


  public AvroMeasurementStoreReader(final Path infoFile, final Path... dataFiles) {
    this.infoFile = infoFile;
    this.dataFiles = dataFiles;
  }

  public static List<Path> lookupObservationFiles(final Path infoFile) throws IOException {
    List<Path> dataFiles = new ArrayList<>(4);
    Path fn = infoFile.getFileName();
    if (fn == null) {
      throw new IllegalArgumentException("Invalid info file " + infoFile);
    }
    String fileName = fn.toString();
    String prefix = fileName.substring(0, fileName.length() - ".tabledef.avro".length());
    Path parent = infoFile.getParent();
    if (parent == null) {
      throw new IllegalArgumentException("Invalid info file " + infoFile);
    }
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent,
            new DirectoryStream.Filter<Path>() {
      @Override
      public boolean accept(final Path entry) {
        if (Files.isDirectory(entry)) {
          return false;
        }
        Path fnp = entry.getFileName();
        if (fnp == null) {
          return false;
        }
        String fName = fnp.toString();
        return fName.startsWith(prefix) && fName.endsWith(".observation.avro");
      }
    })) {
      for (Path f : stream) {
        dataFiles.add(f);
      }
    }
    return dataFiles;
  }

  @Override
  public Collection<Schema> getMeasurements(final Predicate<String> filter) throws IOException {
    Map<String, Pair<Schema, Set<Long>>> result = new THashMap<>();
    try (DataFileStream<TableDef> stream = new DataFileStream<TableDef>(Files.newInputStream(infoFile),
            new SpecificDatumReader<>(TableDef.class))) {
      for (TableDef td : stream) {
        String name = td.getName();
        if (filter.test(name)) {
          Pair<Schema, Set<Long>> exSch = result.get(name);
          if (exSch == null) {
            Schema sch = TableDefs.createSchema(td);
            Set<Long> ids = new HashSet<>(2);
            ids.add(td.getId());
            exSch = Pair.of(sch, ids);
            result.put(name, exSch);
          } else {
            exSch.getValue().add(td.getId());
          }
        }
      }
    }
    return result.values().stream().map((x) -> {
      Schema sch = x.getKey();
      sch.addProp(TimeSeriesRecord.IDS_PROP, x.getValue());
      return sch;
    }).collect(Collectors.toCollection(() -> new ArrayList<>(result.size())));
  }

  @Override
  public AvroCloseableIterable<Observation> getObservations() throws IOException {
    Schema oSchema = Observation.getClassSchema();
    if (dataFiles.length == 0) {
          return AvroCloseableIterable.from(Collections.emptyList(), () -> { }, oSchema);
    }
    SpecificDatumReader<Observation> specificDatumReader = new SpecificDatumReader<>(Observation.class);
    Iterable<Observation>[] streams = new Iterable[dataFiles.length];
    Closeable[] closeables = new Closeable[dataFiles.length];
    for (int i = 0; i < dataFiles.length; i++) {
      Path dataFile = dataFiles[i];
      DataFileStream<Observation> ds;
      try {
        ds =  new DataFileStream<Observation>(Files.newInputStream(dataFile), specificDatumReader);
      } catch (IOException ex) {
        IOException ex2 = Closeables.closeAll(closeables, 0, i);
        if (ex2 != null) {
          ex2.addSuppressed(ex);
          throw ex2;
        }
        throw ex;
      }
      long fileTimeRef = ds.getMetaLong("timeRef");
      streams[i] = Iterables.transform(ds, new TimeCalibrate(fileTimeRef));
      closeables[i] = ds;
    }
    Iterable<Observation> stream = Iterables.concat(streams);
    return AvroCloseableIterable.from(stream, () -> {
      IOException ex = Closeables.closeAll(closeables);
      if (ex != null) {
        throw new UncheckedIOException(ex);
      }
    }, oSchema);
  }

  @Override
  public String toString() {
    return "AvroMeasurementStoreReader{" + "infoFile=" + infoFile + ", dataFiles=" + Arrays.toString(dataFiles) + '}';
  }

  private static class TimeCalibrate implements Function<Observation, Observation> {

    private final long fileTimeRef;

    TimeCalibrate(final long fileTimeRef) {
      this.fileTimeRef = fileTimeRef;
    }

    @Override
    @SuppressFBWarnings({"CFS_CONFUSING_FUNCTION_SEMANTICS", "NP_METHOD_PARAMETER_TIGHTENS_ANNOTATION" })
    // confusing but fast.
    public Observation apply(@Nonnull final Observation row) {
      row.setRelTimeStamp(fileTimeRef + row.getRelTimeStamp());
      return row;
    }
  }


}
