package io.metersphere.system.controller.param;

import io.metersphere.sdk.constants.TemplateScene;
import io.metersphere.sdk.valid.EnumValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class StatusItemAddRequestDefinition implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "组织ID或项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(min = 1, max = 50)
    private String scopeId;

    @Schema(description = "状态ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{status_item.id.not_blank}")
    @Size(min = 1, max = 50, message = "{status_item.id.length_range}")
    private String id;

    @Schema(description = "状态名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{status_item.name.not_blank}")
    @Size(min = 1, max = 255, message = "{status_item.name.length_range}")
    private String name;

    @Schema(description = "使用场景", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{status_item.scene.not_blank}")
    @EnumValue(enumClass = TemplateScene.class)
    private String scene;
}