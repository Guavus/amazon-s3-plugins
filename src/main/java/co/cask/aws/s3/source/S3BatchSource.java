/*
 * Copyright © 2015 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.aws.s3.source;

import co.cask.cdap.api.annotation.Description;
import co.cask.cdap.api.annotation.Macro;
import co.cask.cdap.api.annotation.Name;
import co.cask.cdap.api.annotation.Plugin;
import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.api.plugin.EndpointPluginContext;
import co.cask.cdap.etl.api.batch.BatchSource;
import co.cask.cdap.etl.api.batch.BatchSourceContext;
import co.cask.hydrator.common.LineageRecorder;
import co.cask.hydrator.format.FileFormat;
import co.cask.hydrator.format.input.PathTrackingInputFormat;
import co.cask.hydrator.format.plugin.AbstractFileSource;
import co.cask.hydrator.format.plugin.AbstractFileSourceConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.ws.rs.Path;

/**
 * A {@link BatchSource} that reads from Amazon S3.
 */
@Plugin(type = BatchSource.PLUGIN_TYPE)
@Name("S3")
@Description("Batch source to use Amazon S3 as a source.")
public class S3BatchSource extends AbstractFileSource<S3BatchSource.S3BatchConfig> {
  private static final String S3A_ACCESS_KEY = "fs.s3a.access.key";
  private static final String S3A_SECRET_KEY = "fs.s3a.secret.key";
  private static final String S3A_ENDPOINT = "fs.s3a.endpoint";
  private static final String ACCESS_CREDENTIALS = "Access Credentials";

  @SuppressWarnings("unused")
  private final S3BatchConfig config;

  public S3BatchSource(S3BatchConfig config) {
    super(config);
    this.config = config;
  }

  @Override
  protected Map<String, String> getFileSystemProperties(BatchSourceContext context) {

    String authenticationMethod = config.authenticationMethod;
    Map<String, String> properties = new HashMap<>(config.getFilesystemProperties());
    if (authenticationMethod != null && authenticationMethod.equalsIgnoreCase(ACCESS_CREDENTIALS)) {
      if (config.path.startsWith("s3a://")) {
        properties.put(S3A_ACCESS_KEY, config.accessID);
        properties.put(S3A_SECRET_KEY, config.accessKey);
        String endPoint = "s3." + config.region + ".amazonaws.com";
        if (!(properties.containsKey(S3A_ENDPOINT))) {
          properties.put(S3A_ENDPOINT, endPoint);
        }
      }
    }
    if (config.shouldCopyHeader()) {
      properties.put(PathTrackingInputFormat.COPY_HEADER, "true");
    }
    return properties;
  }

  @Override
  protected void recordLineage(LineageRecorder lineageRecorder, List<String> outputFields) {
    lineageRecorder.recordRead("Read", "Read from S3.", outputFields);
  }

  /**
   * Endpoint method to get the output schema of a source.
   *
   * @param config configuration for the source
   * @param pluginContext context to create plugins
   * @return schema of fields
   */
  @Path("getSchema")
  public Schema getSchema(S3BatchConfig config, EndpointPluginContext pluginContext) {
    FileFormat fileFormat = config.getFormat();
    if (fileFormat == null) {
      return config.getSchema();
    }
    Schema schema = fileFormat.getSchema(config.getPathField());
    return schema == null ? config.getSchema() : schema;
  }

  /**
   * Config class that contains properties needed for the S3 source.
   */
  @SuppressWarnings("unused")
  public static class S3BatchConfig extends AbstractFileSourceConfig {
    private static final Gson GSON = new Gson();
    private static final Type MAP_STRING_STRING_TYPE = new TypeToken<Map<String, String>>() { }.getType();

    @Macro
    @Description("Path to file(s) to be read. If a directory is specified, terminate the path name with a '/'. " +
      "The path must start with s3a://.")
    private String path;

    @Macro
    @Nullable
    @Description("Access ID of the Amazon S3 instance to connect to.")
    private String accessID;

    @Macro
    @Nullable
    @Description("Access Key of the Amazon S3 instance to connect to.")
    private String accessKey;

    @Macro
    @Nullable
    @Description("Region of the Amazon S3 instance to connect to.")
    private String region;

    @Macro
    @Nullable
    @Description("Authentication method to access S3. " +
      "Defaults to Access Credentials. URI scheme should be s3a:// for S3AFileSystem")
    private String authenticationMethod;

    @Macro
    @Nullable
    @Description("Any additional properties to use when reading from the filesystem. " +
      "This is an advanced feature that requires knowledge of the properties supported by the underlying filesystem.")
    private String fileSystemProperties;

    public S3BatchConfig() {
      authenticationMethod = ACCESS_CREDENTIALS;
      fileSystemProperties = GSON.toJson(Collections.emptyMap());
    }

    @Override
    public void validate() {
      super.validate();
      if (ACCESS_CREDENTIALS.equals(authenticationMethod)) {
        if (!containsMacro("accessID") && (accessID == null || accessID.isEmpty())) {
          throw new IllegalArgumentException("The Access ID must be specified if " +
                                               "authentication method is Access Credentials.");
        }
        if (!containsMacro("accessKey") && (accessKey == null || accessKey.isEmpty())) {
          throw new IllegalArgumentException("The Access Key must be specified if " +
                                               "authentication method is Access Credentials.");
        }
      }

      if (!containsMacro("region") && (region == null || region.isEmpty())) {
        throw new IllegalArgumentException("Non-empty Region must be specified.");
      }

      if (!containsMacro("path") && !path.startsWith("s3a://")) {
        throw new IllegalArgumentException("Path must start with s3a:// for S3AFileSystem");
      }
    }

    @Override
    public String getPath() {
      return path;
    }

    Map<String, String> getFilesystemProperties() {
      Map<String, String> properties = new HashMap<>();
      if (containsMacro("fileSystemProperties")) {
        return properties;
      }
      return GSON.fromJson(fileSystemProperties, MAP_STRING_STRING_TYPE);
    }
  }
}
