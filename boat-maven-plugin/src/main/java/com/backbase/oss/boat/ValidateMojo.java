package com.backbase.oss.boat;

import com.backbase.oss.boat.serializer.SerializerUtils;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.io.File;
import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Validates OpenAPI specs.
 */
@Mojo(name = "validate", requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
@Slf4j
public class ValidateMojo extends AbstractMojo {

    @Parameter(name = "input", required = true)
    private File input;

    @Parameter(name = "failOnWarning", required = true)
    private boolean failOnWarning;

    public void setInput(File input) {
        this.input = input;
    }
    public void setFailOnWarning(boolean failOnWarning) {
        this.failOnWarning= failOnWarning;
    }

    @Override
    public void execute() throws  MojoFailureException {

        if (!input.exists()) {
            throw new MojoFailureException("File not found: " + input.getName());
        }

        if (input.isDirectory()) {
            log.info("Validating files '*.yaml' in: {}", input);
            for (File inputFile: input.listFiles(pathname -> StringUtils.endsWith(pathname.getName(), ".yaml"))) {
                validate(inputFile);
            }
        } else {
            validate(input);
        }
    }

    private void validate(File inputFile) throws MojoFailureException {

        log.info("Validating {}", inputFile);

        OpenAPIParser openAPIParser = new OpenAPIParser();
        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setFlatten(true);
        parseOptions.setResolveFully(true);

        SwaggerParseResult swaggerParseResult =
            openAPIParser.readLocation(inputFile.toURI().toString(), new ArrayList<>(), parseOptions);

        if (swaggerParseResult.getMessages() != null && swaggerParseResult.getMessages().isEmpty()) {
            log.info("OpenAPI: {} is valid", swaggerParseResult.getOpenAPI().getInfo().getTitle());
        } else {
            if (swaggerParseResult.getMessages() != null)
                for (String message : swaggerParseResult.getMessages()) {
                    processMessages(message, inputFile, swaggerParseResult);
                }
            if (failOnWarning) {
                throw new MojoFailureException("Validation errors validating OpenAPI");
            }
        }
    }

    private void processMessages(String message,File inputFile, SwaggerParseResult swaggerParseResult){
        if (failOnWarning) {
            log.error("Validation errors while parsing OpenAPI: {}", inputFile.getName());
            log.error(message);
        } else {
            log.warn("Validation errors while parsing OpenAPI: {}", inputFile.getName());
            log.warn(message);
        }
        if (log.isDebugEnabled()) {
            try {
                log.debug("Dumping open api");
                log.debug(SerializerUtils.toYamlString(swaggerParseResult.getOpenAPI()));
            } catch (RuntimeException e) {
                log.debug("That did not end well: ", e);
            }
        }
    }
}
