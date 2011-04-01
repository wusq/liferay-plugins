/**
 * Copyright (c) 2000-2011 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.knowledgebase.admin.lar;

import com.liferay.counter.service.CounterLocalServiceUtil;
import com.liferay.documentlibrary.service.DLLocalServiceUtil;
import com.liferay.knowledgebase.model.KBArticle;
import com.liferay.knowledgebase.model.KBArticleConstants;
import com.liferay.knowledgebase.model.KBComment;
import com.liferay.knowledgebase.model.KBTemplate;
import com.liferay.knowledgebase.service.KBArticleLocalServiceUtil;
import com.liferay.knowledgebase.service.KBCommentLocalServiceUtil;
import com.liferay.knowledgebase.service.KBTemplateLocalServiceUtil;
import com.liferay.knowledgebase.service.persistence.KBArticleUtil;
import com.liferay.knowledgebase.service.persistence.KBCommentUtil;
import com.liferay.knowledgebase.service.persistence.KBTemplateUtil;
import com.liferay.knowledgebase.util.KnowledgeBaseUtil;
import com.liferay.knowledgebase.util.PortletKeys;
import com.liferay.knowledgebase.util.comparator.KBArticleModifiedDateComparator;
import com.liferay.knowledgebase.util.comparator.KBArticlePriorityComparator;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.lar.BasePortletDataHandler;
import com.liferay.portal.kernel.lar.PortletDataContext;
import com.liferay.portal.kernel.lar.PortletDataHandlerBoolean;
import com.liferay.portal.kernel.lar.PortletDataHandlerControl;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.model.CompanyConstants;
import com.liferay.portal.model.GroupConstants;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.util.PortalUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.portlet.PortletPreferences;

/**
 * @author Peter Shin
 * @author Brian Wing Shun Chan
 */
public class AdminPortletDataHandlerImpl extends BasePortletDataHandler {

	public PortletDataHandlerControl[] getExportControls() {
		return new PortletDataHandlerControl[] {_kbArticles, _kbTemplates};
	}

	public PortletDataHandlerControl[] getImportControls() {
		return new PortletDataHandlerControl[] {_kbArticles, _kbTemplates};
	}

	protected PortletPreferences doDeleteData(
			PortletDataContext portletDataContext, String portletId,
			PortletPreferences portletPreferences)
		throws Exception {

		if (!portletDataContext.addPrimaryKey(
				AdminPortletDataHandlerImpl.class, "deleteData")) {

			KBArticleLocalServiceUtil.deleteGroupKBArticles(
				portletDataContext.getScopeGroupId());

			KBTemplateLocalServiceUtil.deleteGroupKBTemplates(
				portletDataContext.getScopeGroupId());
		}

		return null;
	}

	protected String doExportData(
			PortletDataContext portletDataContext, String portletId,
			PortletPreferences portletPreferences)
		throws Exception {

		portletDataContext.addPermissions(
			"com.liferay.knowledgebase.admin",
			portletDataContext.getScopeGroupId());

		Document document = SAXReaderUtil.createDocument();

		Element rootElement = document.addElement("knowledge-base-admin-data");

		rootElement.addAttribute(
			"group-id", String.valueOf(portletDataContext.getScopeGroupId()));

		exportKBArticles(portletDataContext, rootElement);

		if (portletDataContext.getBooleanParameter(
				_NAMESPACE_KB_ARTICLE, "kb-comments")) {

			exportKBComments(
				portletDataContext, KBArticle.class, "kb-article-kb-comment",
				rootElement);
		}

		if (portletDataContext.getBooleanParameter(
				_NAMESPACE, "kb-templates")) {

			exportKBTemplates(portletDataContext, rootElement);
		}

		if (portletDataContext.getBooleanParameter(
				_NAMESPACE_KB_TEMPLATE, "kb-comments")) {

			exportKBComments(
				portletDataContext, KBTemplate.class, "kb-template-kb-comment",
				rootElement);
		}

		return document.formattedString();
	}

	protected PortletPreferences doImportData(
			PortletDataContext portletDataContext, String portletId,
			PortletPreferences portletPreferences, String data)
		throws Exception {

		portletDataContext.importPermissions(
			"com.liferay.knowledgebase.admin",
			portletDataContext.getSourceGroupId(),
			portletDataContext.getScopeGroupId());

		Document document = SAXReaderUtil.read(data);

		Element rootElement = document.getRootElement();

		importKBArticles(portletDataContext, rootElement);

		if (portletDataContext.getBooleanParameter(
				_NAMESPACE, "kb-templates")) {

			importKBTemplates(portletDataContext, rootElement);
		}

		return null;
	}

	protected void exportKBArticle(
			PortletDataContext portletDataContext, Element rootElement,
			String path, KBArticle kbArticle)
		throws PortalException, SystemException {

		Element kbArticleElement = rootElement.addElement("kb-article");

		kbArticleElement.addAttribute("path", path);

		kbArticle.setUserUuid(kbArticle.getUserUuid());

		portletDataContext.addZipEntry(path, kbArticle);

		portletDataContext.addPermissions(
			KBArticle.class, kbArticle.getResourcePrimKey());

		exportKBArticleVersions(
			portletDataContext, kbArticleElement,
			kbArticle.getResourcePrimKey());

		if (portletDataContext.getBooleanParameter(
				_NAMESPACE_KB_ARTICLE, "attachments")) {

			exportKBArticleAttachments(
				portletDataContext, rootElement, kbArticle);
		}

		if (portletDataContext.getBooleanParameter(
				_NAMESPACE_KB_ARTICLE, "categories")) {

			portletDataContext.addAssetCategories(
				KBArticle.class, kbArticle.getResourcePrimKey());
		}

		if (portletDataContext.getBooleanParameter(
				_NAMESPACE_KB_ARTICLE, "tags")) {

			portletDataContext.addAssetTags(
				KBArticle.class, kbArticle.getResourcePrimKey());
		}

		if (portletDataContext.getBooleanParameter(
				_NAMESPACE_KB_ARTICLE, "ratings")) {

			portletDataContext.addRatingsEntries(
				KBArticle.class, kbArticle.getResourcePrimKey());
		}
	}

	protected void exportKBArticleAttachments(
			PortletDataContext portletDataContext, Element rootElement,
			KBArticle kbArticle)
		throws PortalException, SystemException {

		Element kbArticleAttachmentsElement = rootElement.addElement(
			"kb-article-attachments");

		kbArticleAttachmentsElement.addAttribute(
			"resource-prim-key",
			String.valueOf(kbArticle.getResourcePrimKey()));

		String rootPath =
			getPortletPath(portletDataContext) + "/kbarticles/attachments/" +
				kbArticle.getResourcePrimKey();

		for (String fileName : kbArticle.getAttachmentsFileNames()) {
			String shortFileName = FileUtil.getShortFileName(fileName);

			String path = rootPath + StringPool.SLASH + shortFileName;
			byte[] bytes = DLLocalServiceUtil.getFile(
				kbArticle.getCompanyId(), CompanyConstants.SYSTEM, fileName);

			Element fileElement = kbArticleAttachmentsElement.addElement(
				"file");

			fileElement.addAttribute("path", path);
			fileElement.addAttribute("short-file-name", shortFileName);

			portletDataContext.addZipEntry(path, bytes);
		}
	}

	protected void exportKBArticleVersions(
			PortletDataContext portletDataContext, Element kbArticleElement,
			long resourcePrimKey)
		throws SystemException {

		Element versionsElement = kbArticleElement.addElement("versions");

		String rootPath =
			getPortletPath(portletDataContext) + "/kbarticles/versions/" +
				resourcePrimKey;

		List<KBArticle> kbArticles = KBArticleUtil.findByR_S(
			resourcePrimKey, WorkflowConstants.STATUS_APPROVED,
			QueryUtil.ALL_POS, QueryUtil.ALL_POS,
			new KBArticleModifiedDateComparator(true));

		for (KBArticle kbArticle : kbArticles) {
			String path =
				rootPath + StringPool.SLASH + kbArticle.getKbArticleId() +
					".xml";

			Element curKBArticleElement = versionsElement.addElement(
				"kb-article");

			curKBArticleElement.addAttribute("path", path);

			portletDataContext.addZipEntry(path, kbArticle);
		}
	}

	protected void exportKBArticles(
			PortletDataContext portletDataContext, Element rootElement)
		throws PortalException, SystemException {

		for (KBArticle kbArticle : getKBArticles(portletDataContext)) {
			if (!portletDataContext.isWithinDateRange(
					kbArticle.getModifiedDate())) {

				continue;
			}

			String path =
				getPortletPath(portletDataContext) + "/kbarticles/" +
					kbArticle.getResourcePrimKey() + ".xml";

			if (!portletDataContext.isPathNotProcessed(path)) {
				continue;
			}

			exportKBArticle(portletDataContext, rootElement, path, kbArticle);
		}
	}

	protected void exportKBComment(
			PortletDataContext portletDataContext, Element rootElement,
			String name, String path, KBComment kbComment)
		throws SystemException {

		Element kbCommentElement = rootElement.addElement(name);

		kbCommentElement.addAttribute("path", path);

		kbComment.setUserUuid(kbComment.getUserUuid());

		portletDataContext.addZipEntry(path, kbComment);
	}

	protected void exportKBComments(
			PortletDataContext portletDataContext, Class<?> classObj,
			String name, Element rootElement)
		throws SystemException {

		long classNameId = PortalUtil.getClassNameId(classObj);

		List<KBComment> kbComments = KBCommentUtil.findByG_C(
			portletDataContext.getScopeGroupId(), classNameId);

		for (KBComment kbComment : kbComments) {
			if (!portletDataContext.isWithinDateRange(
					kbComment.getModifiedDate())) {

				continue;
			}

			String path =
				getPortletPath(portletDataContext) + "/kbcomments/" +
					kbComment.getKbCommentId() + ".xml";

			if (!portletDataContext.isPathNotProcessed(path)) {
				continue;
			}

			exportKBComment(
				portletDataContext, rootElement, name, path, kbComment);
		}
	}

	protected void exportKBTemplate(
			PortletDataContext portletDataContext, Element rootElement,
			String path, KBTemplate kbTemplate)
		throws PortalException, SystemException {

		Element kbTemplateElement = rootElement.addElement("kb-template");

		kbTemplateElement.addAttribute("path", path);

		kbTemplate.setUserUuid(kbTemplate.getUserUuid());

		portletDataContext.addZipEntry(path, kbTemplate);

		portletDataContext.addPermissions(
			KBTemplate.class, kbTemplate.getKbTemplateId());
	}

	protected void exportKBTemplates(
			PortletDataContext portletDataContext, Element rootElement)
		throws PortalException, SystemException {

		List<KBTemplate> kbTemplates = KBTemplateUtil.findByGroupId(
			portletDataContext.getScopeGroupId());

		for (KBTemplate kbTemplate : kbTemplates) {
			if (!portletDataContext.isWithinDateRange(
					kbTemplate.getModifiedDate())) {

				continue;
			}

			String path =
				getPortletPath(portletDataContext) + "/kbtemplates/" +
					kbTemplate.getKbTemplateId() + ".xml";

			if (!portletDataContext.isPathNotProcessed(path)) {
				continue;
			}

			exportKBTemplate(portletDataContext, rootElement, path, kbTemplate);
		}
	}

	protected List<KBArticle> getKBArticles(
			PortletDataContext portletDataContext)
		throws SystemException {

		// Order kbArticles by depth and sort siblings by priority to retain the
		// priority value on import. See KBArticleLocalServiceImpl#getPriority.

		List<KBArticle> kbArticles = new ArrayList<KBArticle>();

		List<KBArticle> siblingKBArticles = new ArrayList<KBArticle>();

		Long[][] params = new Long[][] {
			new Long[] {KBArticleConstants.DEFAULT_PARENT_RESOURCE_PRIM_KEY}
		};

		while ((params = KnowledgeBaseUtil.getParams(params[0])) != null) {
			List<KBArticle> curKBArticles = KBArticleUtil.findByG_P_M(
				portletDataContext.getScopeGroupId(),
				ArrayUtil.toArray(params[1]), true, QueryUtil.ALL_POS,
				QueryUtil.ALL_POS, new KBArticlePriorityComparator(true));

			siblingKBArticles.addAll(curKBArticles);

			if (params[0].length > 0) {
				continue;
			}

			long[] siblingKBArticlesResourcePrimKeys = StringUtil.split(
				ListUtil.toString(siblingKBArticles, "resourcePrimKey"), 0L);

			params[0] = ArrayUtil.toArray(siblingKBArticlesResourcePrimKeys);

			kbArticles.addAll(siblingKBArticles);
			siblingKBArticles.clear();
		}

		return kbArticles;
	}

	protected String getPortletPath(PortletDataContext portletDataContext) {
		return portletDataContext.getPortletPath(
			PortletKeys.KNOWLEDGE_BASE_ADMIN);
	}

	protected void importKBArticle(
			PortletDataContext portletDataContext,
			Map<Long, Long> resourcePrimKeys, Map<String, String> dirNames,
			Element kbArticleElement, KBArticle kbArticle)
		throws Exception {

		long userId = portletDataContext.getUserId(kbArticle.getUserUuid());
		long parentResourcePrimKey = MapUtil.getLong(
			resourcePrimKeys, kbArticle.getParentResourcePrimKey());
		String dirName = MapUtil.getString(
			dirNames, String.valueOf(kbArticle.getResourcePrimKey()));

		long[] assetCategoryIds = null;

		if (portletDataContext.getBooleanParameter(
				_NAMESPACE_KB_ARTICLE, "categories")) {

			assetCategoryIds = portletDataContext.getAssetCategoryIds(
				KBArticle.class, kbArticle.getResourcePrimKey());
		}

		String[] assetTagNames = null;

		if (portletDataContext.getBooleanParameter(
				_NAMESPACE_KB_ARTICLE, "tags")) {

			assetTagNames = portletDataContext.getAssetTagNames(
				KBArticle.class, kbArticle.getResourcePrimKey());
		}

		ServiceContext serviceContext = new ServiceContext();

		serviceContext.setAddCommunityPermissions(true);
		serviceContext.setAddGuestPermissions(true);
		serviceContext.setAssetCategoryIds(assetCategoryIds);
		serviceContext.setAssetTagNames(assetTagNames);
		serviceContext.setCreateDate(kbArticle.getCreateDate());
		serviceContext.setModifiedDate(kbArticle.getModifiedDate());
		serviceContext.setScopeGroupId(portletDataContext.getScopeGroupId());

		KBArticle importedKBArticle = null;

		if (portletDataContext.isDataStrategyMirror()) {
			KBArticle existingKBArticle = KBArticleUtil.fetchByUUID_G(
				kbArticle.getUuid(), portletDataContext.getScopeGroupId());

			if (existingKBArticle == null) {
				importedKBArticle = importKBArticleVersions(
					portletDataContext, kbArticle.getUuid(),
					parentResourcePrimKey, dirName, assetCategoryIds,
					assetTagNames, kbArticleElement);
			}
			else {
				KBArticleLocalServiceUtil.updateKBArticle(
					userId, existingKBArticle.getResourcePrimKey(),
					kbArticle.getTitle(), kbArticle.getContent(),
					kbArticle.getDescription(), dirName, serviceContext);

				KBArticleLocalServiceUtil.moveKBArticle(
					userId, existingKBArticle.getResourcePrimKey(),
					parentResourcePrimKey, kbArticle.getPriority());

				importedKBArticle =
					KBArticleLocalServiceUtil.getLatestKBArticle(
						existingKBArticle.getResourcePrimKey(),
						WorkflowConstants.STATUS_APPROVED);
			}
		}
		else {
			importedKBArticle = importKBArticleVersions(
				portletDataContext, null, parentResourcePrimKey, dirName,
				assetCategoryIds, assetTagNames, kbArticleElement);
		}

		resourcePrimKeys.put(
			kbArticle.getResourcePrimKey(),
			importedKBArticle.getResourcePrimKey());

		portletDataContext.importPermissions(
			KBArticle.class, kbArticle.getResourcePrimKey(),
			importedKBArticle.getResourcePrimKey());

		if (portletDataContext.getBooleanParameter(
				_NAMESPACE_KB_ARTICLE, "ratings")) {

			portletDataContext.importRatingsEntries(
				KBArticle.class, kbArticle.getResourcePrimKey(),
				importedKBArticle.getResourcePrimKey());
		}
	}

	protected void importKBArticleAttachments(
			PortletDataContext portletDataContext, long importId,
			Map<String, String> dirNames, Element rootElement)
		throws Exception {

		List<Element> kbArticleAttachmentsElements = rootElement.elements(
			"kb-article-attachments");

		for (int i = 0; i < kbArticleAttachmentsElements.size(); i++) {
			Element kbArticleAttachmentsElement =
				kbArticleAttachmentsElements.get(i);

			String resourcePrimKey = kbArticleAttachmentsElement.attributeValue(
				"resource-prim-key");

			String dirName =
				"knowledgebase/temp/import/" + importId + StringPool.SLASH +
					resourcePrimKey;

			DLLocalServiceUtil.addDirectory(
				portletDataContext.getCompanyId(), CompanyConstants.SYSTEM,
				dirName);

			List<Element> fileElements = kbArticleAttachmentsElement.elements(
				"file");

			for (Element fileElement : fileElements) {
				String shortFileName = fileElement.attributeValue(
					"short-file-name");

				String fileName = dirName + StringPool.SLASH + shortFileName;
				byte[] bytes = portletDataContext.getZipEntryAsByteArray(
					fileElement.attributeValue("path"));

				ServiceContext serviceContext = new ServiceContext();

				DLLocalServiceUtil.addFile(
					portletDataContext.getCompanyId(),
					CompanyConstants.SYSTEM_STRING,
					GroupConstants.DEFAULT_PARENT_GROUP_ID,
					CompanyConstants.SYSTEM, fileName, 0, StringPool.BLANK,
					serviceContext.getCreateDate(null), serviceContext, bytes);
			}

			dirNames.put(resourcePrimKey, dirName);
		}
	}

	protected KBArticle importKBArticleVersions(
			PortletDataContext portletDataContext, String uuid,
			long parentResourcePrimKey, String dirName, long[] assetCategoryIds,
			String[] assetTagNames, Element kbArticleElement)
		throws Exception {

		Element versionsElement = kbArticleElement.element("versions");

		List<Element> kbArticleElements = versionsElement.elements(
			"kb-article");

		KBArticle importedKBArticle = null;

		for (Element curKBArticleElement : kbArticleElements) {
			KBArticle curKBArticle =
				(KBArticle)portletDataContext.getZipEntryAsObject(
					curKBArticleElement.attributeValue("path"));

			long userId = portletDataContext.getUserId(
				curKBArticle.getUserUuid());

			String curDirName = StringPool.BLANK;
			long[] curAssetCategoryIds = null;
			String[] curAssetTagNames = null;

			int index = kbArticleElements.indexOf(curKBArticleElement);

			if (index == (kbArticleElements.size() - 1)) {
				curDirName = dirName;
				curAssetCategoryIds = assetCategoryIds;
				curAssetTagNames = assetTagNames;
			}

			ServiceContext serviceContext = new ServiceContext();

			serviceContext.setAddCommunityPermissions(true);
			serviceContext.setAddGuestPermissions(true);
			serviceContext.setAssetCategoryIds(curAssetCategoryIds);
			serviceContext.setAssetTagNames(curAssetTagNames);
			serviceContext.setCreateDate(curKBArticle.getCreateDate());
			serviceContext.setModifiedDate(curKBArticle.getModifiedDate());
			serviceContext.setScopeGroupId(
				portletDataContext.getScopeGroupId());

			if (importedKBArticle == null) {
				serviceContext.setUuid(uuid);

				importedKBArticle = KBArticleLocalServiceUtil.addKBArticle(
					userId, parentResourcePrimKey, curKBArticle.getTitle(),
					curKBArticle.getContent(), curKBArticle.getDescription(),
					curDirName, serviceContext);
			}
			else {
				importedKBArticle = KBArticleLocalServiceUtil.updateKBArticle(
					userId, importedKBArticle.getResourcePrimKey(),
					curKBArticle.getTitle(), curKBArticle.getContent(),
					curKBArticle.getDescription(), curDirName, serviceContext);
			}
		}

		return importedKBArticle;
	}

	protected void importKBArticles(
			PortletDataContext portletDataContext, Element rootElement)
		throws Exception {

		long importId = CounterLocalServiceUtil.increment();

		Map<Long, Long> resourcePrimKeys = new HashMap<Long, Long>();
		Map<String, String> dirNames = new HashMap<String, String>();

		try {
			if (portletDataContext.getBooleanParameter(
					_NAMESPACE_KB_ARTICLE, "attachments")) {

				DLLocalServiceUtil.addDirectory(
					portletDataContext.getCompanyId(), CompanyConstants.SYSTEM,
					"knowledgebase/temp/import/" + importId);

				importKBArticleAttachments(
					portletDataContext, importId, dirNames, rootElement);
			}

			List<Element> kbArticleElements = rootElement.elements(
				"kb-article");

			for (Element kbArticleElement : kbArticleElements) {
				String path = kbArticleElement.attributeValue("path");

				if (!portletDataContext.isPathNotProcessed(path)) {
					continue;
				}

				KBArticle kbArticle =
					(KBArticle)portletDataContext.getZipEntryAsObject(path);

				importKBArticle(
					portletDataContext, resourcePrimKeys, dirNames,
					kbArticleElement, kbArticle);
			}

			importKBComments(
				portletDataContext, "kb-article-kb-comment", resourcePrimKeys,
				rootElement);
		}
		finally {
			if (portletDataContext.getBooleanParameter(
					_NAMESPACE_KB_ARTICLE, "attachments")) {

				DLLocalServiceUtil.deleteDirectory(
					portletDataContext.getCompanyId(),
					CompanyConstants.SYSTEM_STRING, CompanyConstants.SYSTEM,
					"knowledgebase/temp/import/" + importId);
			}
		}
	}

	protected void importKBComment(
			PortletDataContext portletDataContext, Map<Long, Long> classPKs,
			KBComment kbComment)
		throws Exception {

		long userId = portletDataContext.getUserId(kbComment.getUserUuid());
		long classPK = MapUtil.getLong(classPKs, kbComment.getClassPK());

		ServiceContext serviceContext = new ServiceContext();

		serviceContext.setCreateDate(kbComment.getCreateDate());
		serviceContext.setModifiedDate(kbComment.getModifiedDate());
		serviceContext.setScopeGroupId(portletDataContext.getScopeGroupId());

		if (portletDataContext.isDataStrategyMirror()) {
			KBComment existingKBComment = KBCommentUtil.fetchByUUID_G(
				kbComment.getUuid(), portletDataContext.getScopeGroupId());

			if (existingKBComment == null) {
				serviceContext.setUuid(kbComment.getUuid());

				KBCommentLocalServiceUtil.addKBComment(
					userId, kbComment.getClassNameId(), classPK,
					kbComment.getContent(), kbComment.getHelpful(),
					serviceContext);
			}
			else {
				KBCommentLocalServiceUtil.updateKBComment(
					existingKBComment.getKbCommentId(),
					kbComment.getClassNameId(), classPK, kbComment.getContent(),
					kbComment.getHelpful(), serviceContext);
			}
		}
		else {
			KBCommentLocalServiceUtil.addKBComment(
				userId, kbComment.getClassNameId(), classPK,
				kbComment.getContent(), kbComment.getHelpful(), serviceContext);
		}
	}

	protected void importKBComments(
			PortletDataContext portletDataContext, String name,
			Map<Long, Long> classPKs, Element rootElement)
		throws Exception {

		List<Element> kbCommentElements = rootElement.elements(name);

		for (Element kbCommentElement : kbCommentElements) {
			String path = kbCommentElement.attributeValue("path");

			if (!portletDataContext.isPathNotProcessed(path)) {
				continue;
			}

			KBComment kbComment =
				(KBComment)portletDataContext.getZipEntryAsObject(path);

			importKBComment(portletDataContext, classPKs, kbComment);
		}
	}

	protected void importKBTemplate(
			PortletDataContext portletDataContext,
			Map<Long, Long> kbTemplateIds, KBTemplate kbTemplate)
		throws Exception {

		long userId = portletDataContext.getUserId(kbTemplate.getUserUuid());

		ServiceContext serviceContext = new ServiceContext();

		serviceContext.setAddCommunityPermissions(true);
		serviceContext.setAddGuestPermissions(true);
		serviceContext.setCreateDate(kbTemplate.getCreateDate());
		serviceContext.setModifiedDate(kbTemplate.getModifiedDate());
		serviceContext.setScopeGroupId(portletDataContext.getScopeGroupId());

		KBTemplate importedKBTemplate = null;

		if (portletDataContext.isDataStrategyMirror()) {
			KBTemplate existingKBTemplate = KBTemplateUtil.fetchByUUID_G(
				kbTemplate.getUuid(), portletDataContext.getScopeGroupId());

			if (existingKBTemplate == null) {
				serviceContext.setUuid(kbTemplate.getUuid());

				importedKBTemplate = KBTemplateLocalServiceUtil.addKBTemplate(
					userId, kbTemplate.getTitle(), kbTemplate.getContent(),
					kbTemplate.getDescription(), serviceContext);
			}
			else {
				importedKBTemplate =
					KBTemplateLocalServiceUtil.updateKBTemplate(
						existingKBTemplate.getKbTemplateId(),
						kbTemplate.getTitle(), kbTemplate.getContent(),
						kbTemplate.getDescription(), serviceContext);
			}
		}
		else {
			importedKBTemplate = KBTemplateLocalServiceUtil.addKBTemplate(
				userId, kbTemplate.getTitle(), kbTemplate.getContent(),
				kbTemplate.getDescription(), serviceContext);
		}

		kbTemplateIds.put(
			kbTemplate.getKbTemplateId(), importedKBTemplate.getKbTemplateId());

		portletDataContext.importPermissions(
			KBTemplate.class, kbTemplate.getKbTemplateId(),
			importedKBTemplate.getKbTemplateId());
	}

	protected void importKBTemplates(
			PortletDataContext portletDataContext, Element rootElement)
		throws Exception {

		Map<Long, Long> kbTemplateIds = new HashMap<Long, Long>();

		for (Element kbTemplateElement : rootElement.elements("kb-template")) {
			String path = kbTemplateElement.attributeValue("path");

			if (!portletDataContext.isPathNotProcessed(path)) {
				continue;
			}

			KBTemplate kbTemplate =
				(KBTemplate)portletDataContext.getZipEntryAsObject(path);

			importKBTemplate(portletDataContext, kbTemplateIds, kbTemplate);
		}

		importKBComments(
			portletDataContext, "kb-template-kb-comment", kbTemplateIds,
			rootElement);
	}

	private static final String _NAMESPACE = "knowledge_base";

	private static final String _NAMESPACE_KB_ARTICLE =
		"knowledge_base_kb_article";

	private static final String _NAMESPACE_KB_TEMPLATE =
		"knowledge_base_kb_template";

	private static PortletDataHandlerControl[] _kbArticleOptions =
		new PortletDataHandlerControl[] {
			new PortletDataHandlerBoolean(_NAMESPACE_KB_ARTICLE, "attachments"),
			new PortletDataHandlerBoolean(_NAMESPACE_KB_ARTICLE, "categories"),
			new PortletDataHandlerBoolean(_NAMESPACE_KB_ARTICLE, "tags"),
			new PortletDataHandlerBoolean(_NAMESPACE_KB_ARTICLE, "ratings"),
			new PortletDataHandlerBoolean(_NAMESPACE_KB_ARTICLE, "kb-comments")
		};

	private static PortletDataHandlerBoolean _kbArticles =
		new PortletDataHandlerBoolean(
			_NAMESPACE, "kb-articles", true, true, _kbArticleOptions);

	private static PortletDataHandlerControl[] _kbTemplateOptions =
		new PortletDataHandlerControl[] {
			new PortletDataHandlerBoolean(_NAMESPACE_KB_TEMPLATE, "kb-comments")
		};

	private static PortletDataHandlerBoolean _kbTemplates =
		new PortletDataHandlerBoolean(
			_NAMESPACE, "kb-templates", true, false, _kbTemplateOptions);

}