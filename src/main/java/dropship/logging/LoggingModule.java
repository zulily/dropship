/*
 * Copyright (C) 2014 zulily, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dropship.logging;

import java.io.PrintStream;
import java.text.SimpleDateFormat;

/**
 * Dagger-like module that provides logging.
 */
public class LoggingModule {

  private TerseLogger provideTerseLogger(SimpleDateFormat format, PrintStream destination) {
    return new TerseLogger(format, destination);
  }

  private VerboseLogger provideVerboseLogger(SimpleDateFormat format, String jvmName, PrintStream destination) {
    return new VerboseLogger(format, jvmName, destination);
  }

  public Logger provideLogger(SimpleDateFormat format, String jvmName, PrintStream destination) {
    return "false".equals(System.getProperty("verbose", "false"))
      ? provideTerseLogger(format, destination)
      : provideVerboseLogger(format, jvmName, destination);
  }
}
