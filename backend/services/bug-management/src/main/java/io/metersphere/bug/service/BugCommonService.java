package io.metersphere.bug.service;

import io.metersphere.bug.domain.*;
import io.metersphere.bug.dto.BugTemplateInjectField;
import io.metersphere.bug.enums.BugPlatform;
import io.metersphere.bug.enums.BugTemplateCustomField;
import io.metersphere.bug.mapper.*;
import io.metersphere.plugin.platform.dto.SelectOption;
import io.metersphere.plugin.platform.dto.request.GetOptionRequest;
import io.metersphere.plugin.platform.spi.AbstractPlatformPlugin;
import io.metersphere.plugin.platform.spi.Platform;
import io.metersphere.project.domain.FileAssociationExample;
import io.metersphere.project.dto.ProjectUserDTO;
import io.metersphere.project.mapper.FileAssociationMapper;
import io.metersphere.project.request.ProjectMemberRequest;
import io.metersphere.project.service.ProjectApplicationService;
import io.metersphere.project.service.ProjectMemberService;
import io.metersphere.sdk.constants.DefaultRepositoryDir;
import io.metersphere.sdk.constants.StorageType;
import io.metersphere.sdk.file.FileRequest;
import io.metersphere.sdk.util.FileAssociationSourceUtil;
import io.metersphere.sdk.util.JSON;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.domain.ServiceIntegration;
import io.metersphere.system.service.FileService;
import io.metersphere.system.service.PlatformPluginService;
import io.metersphere.system.service.PluginLoadService;
import io.metersphere.system.service.UserPlatformAccountService;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author song-cc-rock
 */
@Service
public class BugCommonService {

	@Resource
	private FileService fileService;
	@Resource
	private ExtBugMapper extBugMapper;
	@Resource
	private BugStatusService bugStatusService;
	@Resource
	private BugCommentMapper bugCommentMapper;
	@Resource
	private BugContentMapper bugContentMapper;
	@Resource
	private BugFollowerMapper bugFollowerMapper;
	@Resource
	private PluginLoadService pluginLoadService;
	@Resource
	private BugCustomFieldMapper bugCustomFieldMapper;
	@Resource
	private ProjectMemberService projectMemberService;
	@Resource
	private BugRelationCaseMapper bugRelationCaseMapper;
	@Resource
	private PlatformPluginService platformPluginService;
	@Resource
	private FileAssociationMapper fileAssociationMapper;
	@Resource
	private BugLocalAttachmentMapper bugLocalAttachmentMapper;
	@Resource
	private ProjectApplicationService projectApplicationService;
	@Resource
	private UserPlatformAccountService userPlatformAccountService;

	/**
	 * 获取表头处理人选项
	 * @param projectId 项目ID
	 * @return 处理人选项集合
	 */
	public List<SelectOption> getHeaderHandlerOption(String projectId) {
		String platformName = projectApplicationService.getPlatformName(projectId);
		// 需要校验服务集成是否开启
		ServiceIntegration serviceIntegration = projectApplicationService.getPlatformServiceIntegrationWithSyncOrDemand(projectId, true);
		if (StringUtils.equals(platformName, BugPlatform.LOCAL.getName())) {
			// Local处理人
			return getLocalHandlerOption(projectId);
		} else {
			// 第三方平台处理人
			// 获取插件中自定义的注入字段(处理人)
			Platform platform = platformPluginService.getPlatform(serviceIntegration.getPluginId(), serviceIntegration.getOrganizationId(),
					new String(serviceIntegration.getConfiguration()));
			List<SelectOption> platformHandlerOption = new ArrayList<>();
			List<BugTemplateInjectField> platformInjectFields = getPlatformInjectFields(projectId);
			for (BugTemplateInjectField injectField : platformInjectFields) {
				if (StringUtils.equals(injectField.getKey(), BugTemplateCustomField.HANDLE_USER.getId())) {
					GetOptionRequest request = new GetOptionRequest();
					request.setOptionMethod(injectField.getOptionMethod());
					request.setProjectConfig(projectApplicationService.getProjectBugThirdPartConfig(projectId));
					platformHandlerOption = platform.getFormOptions(request);
				}
			}
			return platformHandlerOption;
		}
	}

	/**
	 * 项目成员选项(处理人)
	 * @param projectId 项目ID
	 * @return 处理人选项集合
	 */
	public List<SelectOption> getLocalHandlerOption(String projectId) {
		ProjectMemberRequest request = new ProjectMemberRequest();
		request.setProjectId(projectId);
		List<ProjectUserDTO> projectMembers = projectMemberService.listMember(request);
		return projectMembers.stream().map(user -> {
			SelectOption option = new SelectOption();
			option.setText(user.getName());
			option.setValue(user.getId());
			return option;
		}).toList();
	}

	/**
	 * 获取平台注入的字段
	 * @param projectId 项目ID
	 * @return 注入的字段集合
	 */
	public List<BugTemplateInjectField> getPlatformInjectFields(String projectId) {
		// 获取插件中自定义的注入字段(处理人)
		ServiceIntegration serviceIntegration = projectApplicationService.getPlatformServiceIntegrationWithSyncOrDemand(projectId, true);
		AbstractPlatformPlugin platformPlugin = (AbstractPlatformPlugin) pluginLoadService.getMsPluginManager().getPlugin(serviceIntegration.getPluginId()).getPlugin();
		Object scriptContent = pluginLoadService.getPluginScriptContent(serviceIntegration.getPluginId(), platformPlugin.getProjectBugTemplateInjectField());
		String injectFields = JSON.toJSONString(((Map<?, ?>) scriptContent).get("injectFields"));
		return JSON.parseArray(injectFields, BugTemplateInjectField.class);
	}

	/**
	 * 清空缺陷关联的资源(删除缺陷)
	 * @param projectId 项目ID
	 * @param bugIds 缺陷ID集合
	 */
	@Transactional(rollbackFor = Exception.class)
	public void clearAssociateResource(String projectId, List<String> bugIds) {
		// 清空附件(关联, 本地上传, 富文本)
		FileAssociationExample example = new FileAssociationExample();
		example.createCriteria().andSourceIdIn(bugIds).andSourceTypeEqualTo(FileAssociationSourceUtil.SOURCE_TYPE_BUG);
		fileAssociationMapper.deleteByExample(example);
		BugLocalAttachmentExample attachmentExample = new BugLocalAttachmentExample();
		attachmentExample.createCriteria().andBugIdIn(bugIds);
		List<BugLocalAttachment> bugLocalAttachments = bugLocalAttachmentMapper.selectByExample(attachmentExample);
		// 附件类型
		bugLocalAttachments.forEach(bugLocalAttachment -> {
			FileRequest fileRequest = new FileRequest();
			fileRequest.setFolder(DefaultRepositoryDir.getBugDir(projectId, bugLocalAttachment.getBugId()) + "/" + bugLocalAttachment.getFileId());
			fileRequest.setFileName(StringUtils.isEmpty(bugLocalAttachment.getFileName()) ? null : bugLocalAttachment.getFileName());
			fileRequest.setStorage(StorageType.MINIO.name());
			try {
				fileService.deleteFile(fileRequest);
			} catch (Exception e) {
				LogUtils.info("清理缺陷相关附件发生错误: " + e.getMessage());
			}
		});
		bugLocalAttachmentMapper.deleteByExample(attachmentExample);
		// 清空缺陷内容
		BugContentExample contentExample = new BugContentExample();
		contentExample.createCriteria().andBugIdIn(bugIds);
		bugContentMapper.deleteByExample(contentExample);
		// 清除自定义字段关系
		BugCustomFieldExample customFieldExample = new BugCustomFieldExample();
		customFieldExample.createCriteria().andBugIdIn(bugIds);
		bugCustomFieldMapper.deleteByExample(customFieldExample);
		// 清空关联用例
		BugRelationCaseExample relationCaseExample = new BugRelationCaseExample();
		relationCaseExample.createCriteria().andBugIdIn(bugIds);
		bugRelationCaseMapper.deleteByExample(relationCaseExample);
		// 清空评论
		BugCommentExample commentExample = new BugCommentExample();
		commentExample.createCriteria().andBugIdIn(bugIds);
		bugCommentMapper.deleteByExample(commentExample);
		// 清空关注人
		BugFollowerExample followerExample = new BugFollowerExample();
		followerExample.createCriteria().andBugIdIn(bugIds);
		bugFollowerMapper.deleteByExample(followerExample);
	}

	/**
	 * 获取状态选项
	 * @param projectId 项目ID
	 * @return 状态集合
	 */
	public Map<String, String> getAllStatusMap(String projectId) {
		// 缺陷表头状态选项
		List<SelectOption> headerOptions = bugStatusService.getHeaderStatusOption(projectId);
		List<SelectOption> localOptions = bugStatusService.getAllLocalStatusOptions(projectId);
		List<SelectOption> allStatusOption = ListUtils.union(headerOptions, localOptions).stream().distinct().toList();
		return allStatusOption.stream().collect(Collectors.toMap(SelectOption::getValue, SelectOption::getText));
	}

	/**
	 * 获取Local状态流结束标识
	 * @param projectId 项目ID
	 * @return 状态流结束标识集合
	 */
	public List<String> getLocalLastStepStatus(String projectId) {
		return extBugMapper.getLocalLastStepStatusIds(projectId);
	}

	/**
	 * 获取平台状态流结束标识
	 * @param projectId 项目ID
	 * @return 状态流结束标识集合
	 */
	public List<String> getPlatformLastStepStatus(String projectId) throws Exception {
		Platform platform = projectApplicationService.getPlatform(projectId, true);
		List<SelectOption> platformLastSteps = platform.getStatusTransitionsLastSteps(projectApplicationService.getProjectBugThirdPartConfig(projectId));
		return platformLastSteps.stream().map(SelectOption::getValue).toList();
	}

	/**
	 * 获取登录用户平台处理人
	 * @param projectId 项目ID
	 * @param currentUserId 当前用户ID
	 * @param currentOrgId 当前组织ID
	 * @return 平台处理人
	 */
	public String getPlatformHandlerUser(String projectId, String currentUserId, String currentOrgId) {
		ServiceIntegration serviceIntegration = projectApplicationService.getPlatformServiceIntegrationWithSyncOrDemand(projectId, true);
		if (serviceIntegration == null) {
			return null;
		}
		String platformUserName = userPlatformAccountService.getPlatformUserName(currentUserId, currentOrgId, serviceIntegration.getPluginId());
		List<SelectOption> headerHandlerOption = getHeaderHandlerOption(projectId);
		if (StringUtils.isNotBlank(platformUserName)) {
			Optional<SelectOption> handleOption = headerHandlerOption.stream().filter(option -> StringUtils.containsAnyIgnoreCase(option.getText(), platformUserName))
					.findFirst();
			if (handleOption.isPresent()) {
				return handleOption.get().getValue();
			}
			return platformUserName;
		}
		return null;
	}
}
