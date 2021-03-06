/*
 * Copyright 2018 OpenAPI-Generator Contributors (https://openapi-generator.tech)
 * Copyright 2018 SmartBear Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openapitools.codegen.languages;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.DefaultCodegen;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.utils.ModelUtils;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CppPistacheServerCodegen extends AbstractCppCodegen {
    protected String implFolder = "impl";

    @Override
    public CodegenType getTag() {
        return CodegenType.SERVER;
    }

    @Override
    public String getName() {
        return "cpp-pistache-server";
    }

    @Override
    public String getHelp() {
        return "Generates a C++ API server (based on Pistache)";
    }

    public CppPistacheServerCodegen() {
        super();

        apiPackage = "org.openapitools.server.api";
        modelPackage = "org.openapitools.server.model";

        modelTemplateFiles.put("model-header.mustache", ".h");
        modelTemplateFiles.put("model-source.mustache", ".cpp");

        apiTemplateFiles.put("api-header.mustache", ".h");
        apiTemplateFiles.put("api-source.mustache", ".cpp");
        apiTemplateFiles.put("api-impl-header.mustache", ".h");
        apiTemplateFiles.put("api-impl-source.mustache", ".cpp");
        apiTemplateFiles.put("main-api-server.mustache", ".cpp");

        embeddedTemplateDir = templateDir = "pistache-server";

        cliOptions.clear();

        reservedWords = new HashSet<>();

        supportingFiles.add(new SupportingFile("modelbase-header.mustache", "model", "ModelBase.h"));
        supportingFiles.add(new SupportingFile("modelbase-source.mustache", "model", "ModelBase.cpp"));
        supportingFiles.add(new SupportingFile("cmake.mustache", "", "CMakeLists.txt"));
        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md"));

        languageSpecificPrimitives = new HashSet<String>(
                Arrays.asList("int", "char", "bool", "long", "float", "double", "int32_t", "int64_t"));

        typeMapping = new HashMap<String, String>();
        typeMapping.put("date", "std::string");
        typeMapping.put("DateTime", "std::string");
        typeMapping.put("string", "std::string");
        typeMapping.put("integer", "int32_t");
        typeMapping.put("long", "int64_t");
        typeMapping.put("boolean", "bool");
        typeMapping.put("array", "std::vector");
        typeMapping.put("map", "std::map");
        typeMapping.put("file", "std::string");
        typeMapping.put("object", "Object");
        typeMapping.put("binary", "std::string");
        typeMapping.put("number", "double");
        typeMapping.put("UUID", "std::string");

        super.importMapping = new HashMap<String, String>();
        importMapping.put("std::vector", "#include <vector>");
        importMapping.put("std::map", "#include <map>");
        importMapping.put("std::string", "#include <string>");
        importMapping.put("Object", "#include \"Object.h\"");
    }

    @Override
    public void processOpts() {
        super.processOpts();

        additionalProperties.put("modelNamespaceDeclarations", modelPackage.split("\\."));
        additionalProperties.put("modelNamespace", modelPackage.replaceAll("\\.", "::"));
        additionalProperties.put("apiNamespaceDeclarations", apiPackage.split("\\."));
        additionalProperties.put("apiNamespace", apiPackage.replaceAll("\\.", "::"));
    }

    /**
     * Escapes a reserved word as defined in the `reservedWords` array. Handle
     * escaping those terms here. This logic is only called if a variable
     * matches the reserved words
     *
     * @return the escaped term
     */
    @Override
    public String escapeReservedWord(String name) {
        return "_" + name; // add an underscore to the name
    }

    @Override
    public String toModelImport(String name) {
        if (importMapping.containsKey(name)) {
            return importMapping.get(name);
        } else {
            return "#include \"" + name + ".h\"";
        }
    }

    @Override
    public CodegenModel fromModel(String name, Schema model, Map<String, Schema> allDefinitions) {
        CodegenModel codegenModel = super.fromModel(name, model, allDefinitions);

        Set<String> oldImports = codegenModel.imports;
        codegenModel.imports = new HashSet<>();
        for (String imp : oldImports) {
            String newImp = toModelImport(imp);
            if (!newImp.isEmpty()) {
                codegenModel.imports.add(newImp);
            }
        }

        return codegenModel;
    }

    @Override
    public CodegenOperation fromOperation(String path, String httpMethod, Operation operation,
                                          Map<String, Schema> definitions, OpenAPI openAPI) {
        CodegenOperation op = super.fromOperation(path, httpMethod, operation, definitions, openAPI);

        if (operation.getResponses() != null && !operation.getResponses().isEmpty()) {
            ApiResponse apiResponse = findMethodResponse(operation.getResponses());

            if (apiResponse != null) {
                Schema response = ModelUtils.getSchemaFromResponse(apiResponse);
                if (response != null) {
                    CodegenProperty cm = fromProperty("response", response);
                    op.vendorExtensions.put("x-codegen-response", cm);
                    if ("HttpContent".equals(cm.datatype)) {
                        op.vendorExtensions.put("x-codegen-response-ishttpcontent", true);
                    }
                }
            }
        }

        String pathForPistache = path.replaceAll("\\{(.*?)}", ":$1");
        op.vendorExtensions.put("x-codegen-pistache-path", pathForPistache);

        return op;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> postProcessOperations(Map<String, Object> objs) {
        Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        String classname = (String) operations.get("classname");
        operations.put("classnameSnakeUpperCase", DefaultCodegen.underscore(classname).toUpperCase());
        operations.put("classnameSnakeLowerCase", DefaultCodegen.underscore(classname).toLowerCase());

        List<CodegenOperation> operationList = (List<CodegenOperation>) operations.get("operation");
        for (CodegenOperation op : operationList) {
            boolean consumeJson = false;
            boolean isParsingSupported = true;
            if (op.bodyParam != null) {
                if (op.bodyParam.vendorExtensions == null) {
                    op.bodyParam.vendorExtensions = new HashMap<>();
                }

                op.bodyParam.vendorExtensions.put("x-codegen-pistache-isStringOrDate", op.bodyParam.isString || op.bodyParam.isDate);
            }
            if (op.consumes != null) {
                for (Map<String, String> consume : op.consumes) {
                    if (consume.get("mediaType") != null && consume.get("mediaType").equals("application/json")) {
                        consumeJson = true;
                    }
                }
            }

            op.httpMethod = op.httpMethod.substring(0, 1).toUpperCase() + op.httpMethod.substring(1).toLowerCase();

            for (CodegenParameter param : op.allParams) {
                if (param.isFormParam) isParsingSupported = false;
                if (param.isFile) isParsingSupported = false;
                if (param.isCookieParam) isParsingSupported = false;

                //TODO: This changes the info about the real type but it is needed to parse the header params
                if (param.isHeaderParam) {
                    param.dataType = "Optional<Net::Http::Header::Raw>";
                    param.baseType = "Optional<Net::Http::Header::Raw>";
                } else if (param.isQueryParam) {
                    if (param.isPrimitiveType) {
                        param.dataType = "Optional<" + param.dataType + ">";
                    } else {
                        param.dataType = "Optional<" + param.baseType + ">";
                        param.baseType = "Optional<" + param.baseType + ">";
                    }
                }
            }

            if (op.vendorExtensions == null) {
                op.vendorExtensions = new HashMap<>();
            }
            op.vendorExtensions.put("x-codegen-pistache-consumesJson", consumeJson);
            op.vendorExtensions.put("x-codegen-pistache-isParsingSupported", isParsingSupported);
        }

        return objs;
    }

    @Override
    public String toModelFilename(String name) {
        return initialCaps(name);
    }

    @Override
    public String apiFilename(String templateName, String tag) {
        String result = super.apiFilename(templateName, tag);

        if (templateName.endsWith("impl-header.mustache")) {
            int ix = result.lastIndexOf(File.separatorChar);
            result = result.substring(0, ix) + result.substring(ix, result.length() - 2) + "Impl.h";
            result = result.replace(apiFileFolder(), implFileFolder());
        } else if (templateName.endsWith("impl-source.mustache")) {
            int ix = result.lastIndexOf(File.separatorChar);
            result = result.substring(0, ix) + result.substring(ix, result.length() - 4) + "Impl.cpp";
            result = result.replace(apiFileFolder(), implFileFolder());
        } else if (templateName.endsWith("api-server.mustache")) {
            int ix = result.lastIndexOf(File.separatorChar);
            result = result.substring(0, ix) + result.substring(ix, result.length() - 4) + "MainServer.cpp";
            result = result.replace(apiFileFolder(), outputFolder);
        }
        return result;
    }

    @Override
    public String toApiFilename(String name) {
        return initialCaps(name) + "Api";
    }

    /**
     * Optional - type declaration. This is a String which is used by the
     * templates to instantiate your types. There is typically special handling
     * for different property types
     *
     * @return a string value used as the `dataType` field for model templates,
     * `returnType` for api templates
     */
    @Override
    public String getTypeDeclaration(Schema p) {
        String openAPIType = getSchemaType(p);

        if (ModelUtils.isArraySchema(p)) {
            ArraySchema ap = (ArraySchema) p;
            Schema inner = ap.getItems();
            return getSchemaType(p) + "<" + getTypeDeclaration(inner) + ">";
        }
        if (ModelUtils.isMapSchema(p)) {
            Schema inner = (Schema) p.getAdditionalProperties();
            return getSchemaType(p) + "<std::string, " + getTypeDeclaration(inner) + ">";
        }
        if (ModelUtils.isStringSchema(p)
                || ModelUtils.isDateSchema(p)
                || ModelUtils.isDateTimeSchema(p) || ModelUtils.isFileSchema(p)
                || languageSpecificPrimitives.contains(openAPIType)) {
            return toModelName(openAPIType);
        }

        return "std::shared_ptr<" + openAPIType + ">";
    }

    @Override
    public String toDefaultValue(Schema p) {
        if (ModelUtils.isBooleanSchema(p)) {
            return "false";
        } else if (ModelUtils.isDateSchema(p)) {
            return "\"\"";
        } else if (ModelUtils.isDateTimeSchema(p)) {
            return "\"\"";
        } else if (ModelUtils.isNumberSchema(p)) {
            if (ModelUtils.isFloatSchema(p)) {
                return "0.0f";
            }
            return "0.0";
        } else if (ModelUtils.isIntegerSchema(p)) {
            if (ModelUtils.isLongSchema(p)) {
                return "0L";
            }
            return "0";
        } else if (ModelUtils.isMapSchema(p)) {
            String inner = getSchemaType((Schema) p.getAdditionalProperties());
            return "std::map<std::string, " + inner + ">()";
        } else if (ModelUtils.isArraySchema(p)) {
            ArraySchema ap = (ArraySchema) p;
            String inner = getSchemaType(ap.getItems());
            if (!languageSpecificPrimitives.contains(inner)) {
                inner = "std::shared_ptr<" + inner + ">";
            }
            return "std::vector<" + inner + ">()";
        } else if (!StringUtils.isEmpty(p.get$ref())) { // model
            return "new " + toModelName(ModelUtils.getSimpleRef(p.get$ref())) + "()";
        } else if (ModelUtils.isStringSchema(p)) {
            return "\"\"";
        }

        return "nullptr";
    }

    @Override
    public void postProcessParameter(CodegenParameter parameter) {
        super.postProcessParameter(parameter);

        boolean isPrimitiveType = parameter.isPrimitiveType == Boolean.TRUE;
        boolean isListContainer = parameter.isListContainer == Boolean.TRUE;
        boolean isString = parameter.isString == Boolean.TRUE;

        if (!isPrimitiveType && !isListContainer && !isString && !parameter.dataType.startsWith("std::shared_ptr")) {
            parameter.dataType = "std::shared_ptr<" + parameter.dataType + ">";
        }
    }

    /**
     * Location to write model files. You can use the modelPackage() as defined
     * when the class is instantiated
     */
    public String modelFileFolder() {
        return (outputFolder + "/model").replace("/", File.separator);
    }

    /**
     * Location to write api files. You can use the apiPackage() as defined when
     * the class is instantiated
     */
    @Override
    public String apiFileFolder() {
        return (outputFolder + "/api").replace("/", File.separator);
    }

    private String implFileFolder() {
        return (outputFolder + "/" + implFolder).replace("/", File.separator);
    }

    /**
     * Optional - OpenAPI type conversion. This is used to map OpenAPI types in
     * a `Schema` into either language specific types via `typeMapping` or
     * into complex models if there is not a mapping.
     *
     * @return a string value of the type or complex model for this property
     */
    @Override
    public String getSchemaType(Schema p) {
        String openAPIType = super.getSchemaType(p);
        String type = null;
        if (typeMapping.containsKey(openAPIType)) {
            type = typeMapping.get(openAPIType);
            if (languageSpecificPrimitives.contains(type))
                return toModelName(type);
        } else
            type = openAPIType;
        return toModelName(type);
    }

    @Override
    public String toModelName(String type) {
        if (typeMapping.keySet().contains(type) || typeMapping.values().contains(type)
                || importMapping.values().contains(type) || defaultIncludes.contains(type)
                || languageSpecificPrimitives.contains(type)) {
            return type;
        } else {
            return Character.toUpperCase(type.charAt(0)) + type.substring(1);
        }
    }

    @Override
    public String toApiName(String type) {
        return Character.toUpperCase(type.charAt(0)) + type.substring(1) + "Api";
    }

    @Override
    public String escapeQuotationMark(String input) {
        // remove " to avoid code injection
        return input.replace("\"", "");
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        return input.replace("*/", "*_/").replace("/*", "/_*");
    }
}
