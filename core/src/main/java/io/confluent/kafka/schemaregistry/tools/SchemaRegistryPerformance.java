/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.kafka.schemaregistry.tools;

import io.confluent.common.utils.AbstractPerformanceTest;
import io.confluent.common.utils.PerformanceStats;
import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import io.confluent.kafka.schemaregistry.avro.AvroUtils;
import io.confluent.kafka.schemaregistry.client.rest.RestService;
import io.confluent.kafka.schemaregistry.client.rest.entities.requests.ConfigUpdateRequest;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;

import java.io.IOException;

public class SchemaRegistryPerformance extends AbstractPerformanceTest {

  long targetRegisteredSchemas;
  long targetSchemasPerSec;
  String baseUrl;
  RestService restService;
  String subject;
  long registeredSchemas = 0;
  long successfullyRegisteredSchemas = 0;

  public static void main(String[] args) throws Exception {

    if (args.length < 4) {
      System.out.println(
          "Usage: java " + SchemaRegistryPerformance.class.getName() + " schema_registry_url"
          + " subject num_schemas target_schemas_per_sec"
      );
      System.exit(1);
    }

    String baseUrl = args[0];
    String subject = args[1];
    int numSchemas = Integer.parseInt(args[2]);
    int targetSchemasPerSec = Integer.parseInt(args[3]);

    SchemaRegistryPerformance perf =
        new SchemaRegistryPerformance(baseUrl, subject, numSchemas, targetSchemasPerSec);
    perf.run(targetSchemasPerSec);
    perf.close();
  }

  public SchemaRegistryPerformance(String baseUrl, String subject, long numSchemas,
                                   long targetSchemasPerSec) throws Exception {
    super(numSchemas);
    this.baseUrl = baseUrl;
    this.restService = new RestService(baseUrl);
    this.subject = subject;
    this.targetRegisteredSchemas = numSchemas;
    this.targetSchemasPerSec = targetSchemasPerSec;

    // No compatibility verification
    ConfigUpdateRequest request = new ConfigUpdateRequest();
    request.setCompatibilityLevel(CompatibilityLevel.NONE.name);
    restService.updateConfig(request, null);
  }

  // sequential schema maker
  private static String makeSchema(long num) {
    String schemaString = "{\"type\":\"record\","
                          + "\"name\":\"myrecord\","
                          + "\"fields\":"
                          + "[{\"type\":\"string\",\"name\":"
                          + "\"f" + num + "\"}]}";
    return AvroUtils.parseSchema(schemaString).canonicalString();
  }

  @Override
  protected void doIteration(PerformanceStats.Callback cb) {
    String schema = makeSchema(this.registeredSchemas);

    try {
      restService.registerSchema(schema, this.subject);
      successfullyRegisteredSchemas++;
    } catch (IOException e) {
      System.out.println("Problem registering schema: " + e.getMessage());
    } catch (RestClientException e) {
      System.out.println("Problem registering schema: " + e.getMessage());
    }

    registeredSchemas++;
    cb.onCompletion(1, 0);
  }

  protected void close() {
    // We can see some failures due to things like timeouts, but we want it to be obvious
    // if there are too many failures (indicating a real underlying problem). 1% is an arbitrarily
    // chosen limit.
    if (successfullyRegisteredSchemas / (double) targetRegisteredSchemas < 0.99) {
      throw new RuntimeException("Too many schema registration errors: "
                                 + successfullyRegisteredSchemas
                                 + " registered successfully out of " + targetRegisteredSchemas
                                 + " attempted");
    }
  }

  @Override
  protected boolean finished(int iteration) {
    return this.targetRegisteredSchemas == this.registeredSchemas;
  }

  @Override
  protected boolean runningFast(int iteration, float elapsed) {
    return iteration / elapsed > targetSchemasPerSec;
  }
}

