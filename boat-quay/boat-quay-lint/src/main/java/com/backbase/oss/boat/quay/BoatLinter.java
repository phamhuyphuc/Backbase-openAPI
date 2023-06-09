package com.backbase.oss.boat.quay;

import com.backbase.oss.boat.loader.OpenAPILoader;
import com.backbase.oss.boat.loader.OpenAPILoaderException;
import com.backbase.oss.boat.quay.configuration.RulesValidatorConfiguration;
import com.backbase.oss.boat.quay.model.BoatLintReport;
import com.backbase.oss.boat.quay.model.BoatLintRule;
import com.backbase.oss.boat.quay.model.BoatViolation;
import com.typesafe.config.Config;
import io.swagger.v3.oas.models.OpenAPI;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.zalando.zally.core.ApiValidator;
import org.zalando.zally.core.DefaultContextFactory;
import org.zalando.zally.core.Result;
import org.zalando.zally.core.RulesManager;
import org.zalando.zally.core.RulesPolicy;
import org.zalando.zally.rule.api.Rule;
import org.zalando.zally.rule.api.RuleSet;

@Slf4j
public class BoatLinter {

    private final ApiValidator validator;

    private final URI documentationBaseUrl = URI.create("https://backbase.github.io/backbase-openapi-tools/rules.md");
    private final RulesManager rulesManager;
    private final RulesPolicy rulesPolicy;
    private final Map<String, BoatLintRule> availableRules;
    private final Config config;

    public BoatLinter(String... ignoreRules) {
        RulesValidatorConfiguration rulesValidatorConfiguration = new RulesValidatorConfiguration();
        this.config = rulesValidatorConfiguration.config("boat.conf");
        this.rulesManager = rulesValidatorConfiguration.rulesManager(config);
        this.rulesPolicy = new RulesPolicy(Arrays.asList(ignoreRules));
        this.validator = rulesValidatorConfiguration.apiValidator(rulesManager, new DefaultContextFactory());
        this.availableRules = mapAvailableRules();
    }

    public BoatLintReport lint(File inputFile) throws IOException, OpenAPILoaderException {
        Path relativePath = getFilePath(inputFile);
        log.info("Linting: {}", inputFile);
        String contents = IOUtils.toString(inputFile.toURI(), Charset.defaultCharset());
        BoatLintReport boatLintReport = lint(contents);
        boatLintReport.setFilePath(relativePath.toString());
        return boatLintReport;
    }

    @NotNull
    private Path getFilePath(File inputFile) {
        File workingDirectory = new File(".");

        if (inputFile.isAbsolute()) {
            workingDirectory = workingDirectory.getAbsoluteFile();
        }

        Path relativize;
        try {
            relativize = workingDirectory.toPath().relativize(inputFile.toPath());
        } catch (RuntimeException exception) {
            log.warn("Failed to get relative path for: {} in working directory: {}", inputFile, workingDirectory);
            return inputFile.toPath();
        }
        return relativize;
    }

    public BoatLintReport lint(String openApiContent) throws OpenAPILoaderException {
        List<Result> validate = validator.validate(openApiContent, rulesPolicy, null);
        List<BoatViolation> violations = validate.stream()
            .map(this::transformResult)
            .collect(Collectors.toList());

        OpenAPI openAPI = OpenAPILoader.parse(openApiContent);

        BoatLintReport boatLintReport = new BoatLintReport();
        boatLintReport.setOpenApi(openApiContent);
        boatLintReport.setAvailableRules(getAvailableRules());
        boatLintReport.setViolations(violations);
        boatLintReport.setTitle(openAPI.getInfo().getTitle());
        boatLintReport.setVersion(openAPI.getInfo().getVersion());
        return boatLintReport;
    }

    private BoatViolation transformResult(Result result) {

        BoatViolation violation = new BoatViolation();
        violation.setRule(availableRules.get(result.getId()));
        violation.setDescription(result.getDescription());
        violation.setPointer(result.getPointer());
        violation.setLines(result.getLines());
        violation.setSeverity(result.getViolationType());
        return violation;
    }

    @NotNull
    private URI getUri(String id, String title) {
        String heading = id + ":" + title;
        String ref = heading.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        return documentationBaseUrl.resolve("#" + ref);
    }


    private Map<String, BoatLintRule> mapAvailableRules() {
        Map<String, BoatLintRule> rules = new LinkedHashMap<>();

        Config extraRuleAnnotations = config.getConfig("ExtraRuleAnnotations");
        Config defaultConfig = extraRuleAnnotations.getConfig("default");
        rulesManager.rules(rulesPolicy)
            .forEach(ruleDetails -> {
                long effortInMinutes = getEffortInMinutes(extraRuleAnnotations, defaultConfig, ruleDetails);
                BoatLintRule.Type type = getType(extraRuleAnnotations, defaultConfig, ruleDetails);
                BoatLintRule boatLintRule = new BoatLintRule();
                Rule rule = ruleDetails.getRule();
                RuleSet ruleSet = ruleDetails.getRuleSet();
                boatLintRule.setId(rule.id());
                boatLintRule.setUrl(getUri(rule.id(), rule.title()));
                boatLintRule.setTitle(rule.title());
                boatLintRule.setSeverity(rule.severity());
                boatLintRule.setIgnored(false);
                boatLintRule.setRuleSet(ruleSet.getId());
                boatLintRule.setType(type);
                boatLintRule.setEffortMinutes(effortInMinutes);
                rules.put(rule.id(), boatLintRule);
            });
        return rules;
    }

    private BoatLintRule.Type getType(Config extraRuleAnnotations, Config defaultConfig, org.zalando.zally.core.RuleDetails ruleDetails) {
        String defaultPath = ruleDetails.getRule().severity() + ".type";
        BoatLintRule.Type defaultType = defaultConfig.getEnum(BoatLintRule.Type.class, defaultPath);
        String path = "rules." + ruleDetails.getRule().id() + ".type";
        return extraRuleAnnotations.hasPath(path)
            ? extraRuleAnnotations.getEnum(BoatLintRule.Type.class, path)
            : defaultType;
    }

    private long getEffortInMinutes(Config extraRuleAnnotations, Config defaultConfig, org.zalando.zally.core.RuleDetails ruleDetails) {
        long defaultEffortMinutes = defaultConfig.getLong(ruleDetails.getRule().severity() + ".effortMinutes");
        String path = "rules." + ruleDetails.getRule().id() + ".effortMinutes";
        return extraRuleAnnotations.hasPath(path)
            ? extraRuleAnnotations.getLong(path)
            : defaultEffortMinutes;
    }

    public List<BoatLintRule> getAvailableRules() {
        return new ArrayList<>(availableRules.values());
    }


}
