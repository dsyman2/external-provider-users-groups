/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *     Copyright (C) 2002-2014 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ======================================================================================
 *
 *     IF YOU DECIDE TO CHOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     "This program is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation; either version 2
 *     of the License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *     As a special exception to the terms and conditions of version 2.0 of
 *     the GPL (or any later version), you may redistribute this Program in connection
 *     with Free/Libre and Open Source Software ("FLOSS") applications as described
 *     in Jahia's FLOSS exception. You should have received a copy of the text
 *     describing the FLOSS exception, also available here:
 *     http://www.jahia.com/license"
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ======================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 *
 *
 * ==========================================================================================
 * =                                   ABOUT JAHIA                                          =
 * ==========================================================================================
 *
 *     Rooted in Open Source CMS, Jahia’s Digital Industrialization paradigm is about
 *     streamlining Enterprise digital projects across channels to truly control
 *     time-to-market and TCO, project after project.
 *     Putting an end to “the Tunnel effect”, the Jahia Studio enables IT and
 *     marketing teams to collaboratively and iteratively build cutting-edge
 *     online business solutions.
 *     These, in turn, are securely and easily deployed as modules and apps,
 *     reusable across any digital projects, thanks to the Jahia Private App Store Software.
 *     Each solution provided by Jahia stems from this overarching vision:
 *     Digital Factory, Workspace Factory, Portal Factory and eCommerce Factory.
 *     Founded in 2002 and headquartered in Geneva, Switzerland,
 *     Jahia Solutions Group has its North American headquarters in Washington DC,
 *     with offices in Chicago, Toronto and throughout Europe.
 *     Jahia counts hundreds of global brands and governmental organizations
 *     among its loyal customers, in more than 20 countries across the globe.
 *
 *     For more information, please visit http://www.jahia.com
 */
package org.jahia.modules.external.users;

import org.apache.commons.lang.StringUtils;
import org.jahia.exceptions.JahiaInitializationException;
import org.jahia.modules.external.ExternalContentStoreProvider;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.content.*;
import org.jahia.settings.SettingsBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ExternalUserGroupServiceImpl implements ExternalUserGroupService {

    private static final Logger logger = LoggerFactory.getLogger(ExternalUserGroupServiceImpl.class);

    private static final String PROVIDERS_MOUNT_CONTAINER = "providers";

    private JCRStoreService jcrStoreService;
    private String readOnlyUserProperties;

    public void register(String providerKey, final UserGroupProvider userGroupProvider) {
        final String usersFolderPath = "/users";
        final String groupsFolderPath = "/groups";

        try {
            JCRTemplate.getInstance().doExecuteWithSystemSession(new JCRCallback<Object>() {
                @Override
                public Object doInJCR(JCRSessionWrapper session) throws RepositoryException {
                    boolean saveNeeded = false;
                    JCRNodeWrapper node = session.getNode(usersFolderPath);
                    if (!node.hasNode(PROVIDERS_MOUNT_CONTAINER)) {
                        node.addNode(PROVIDERS_MOUNT_CONTAINER, "jnt:usersFolder");
                        saveNeeded = true;
                    }
                    node = session.getNode(groupsFolderPath);
                    if (userGroupProvider.supportsGroups() && !node.hasNode(PROVIDERS_MOUNT_CONTAINER)) {
                        node.addNode(PROVIDERS_MOUNT_CONTAINER, "jnt:groupsFolder");
                        saveNeeded = true;
                    }
                    if (saveNeeded) {
                        session.save();
                    }
                    return null;
                }
            });
        } catch (RepositoryException e) {
            logger.error("Failed to create providers mount containers", e);
            return;
        }

        String userProviderKey = providerKey + ".users";
        String groupProviderKey = providerKey + ".groups";
        Map<String, JCRStoreProvider> providers = jcrStoreService.getSessionFactory().getProviders();
        if (providers.get(userProviderKey) == null && providers.get(groupProviderKey) == null) {
            try {
                UsersDataSource usersDataSource = (UsersDataSource) SpringContextSingleton.getBeanInModulesContext("UsersDataSourcePrototype");
                usersDataSource.setUserGroupProvider(userGroupProvider);

                ExternalContentStoreProvider userProvider = (ExternalContentStoreProvider) SpringContextSingleton.getBeanInModulesContext("ExternalStoreProviderPrototype");
                userProvider.setKey(userProviderKey);
                userProvider.setMountPoint(usersFolderPath + "/" + PROVIDERS_MOUNT_CONTAINER + "/" + providerKey);
                userProvider.setDataSource(usersDataSource);
                userProvider.setExtendableTypes(Arrays.asList("nt:base"));
                userProvider.setOverridableItems(Arrays.asList("jnt:user.*", "jnt:usersFolder.*", "mix:lastModified.*", "jmix:lastPublished.*"));
                String readOnlyProps = (String) SettingsBean.getInstance().getPropertiesFile().get("external.users.properties.readonly." + providerKey);
                if (StringUtils.isBlank(readOnlyProps)) {
                    readOnlyProps = readOnlyUserProperties;
                }
                if (StringUtils.isNotBlank(readOnlyProps)) {
                    List<String> nonOverridableItems = new ArrayList<String>();
                    for (String p : StringUtils.split(readOnlyProps, ",")) {
                        if (StringUtils.isNotBlank(p)) {
                            nonOverridableItems.add("jnt:user." + p.trim());
                        }
                    }
                    if (!nonOverridableItems.isEmpty()) {
                        userProvider.setNonOverridableItems(nonOverridableItems);
                    }
                }

                usersDataSource.setContentStoreProvider(userProvider);

                userProvider.start();

                if (userGroupProvider.supportsGroups()) {
                    GroupsDataSource groupDataSource = (GroupsDataSource) SpringContextSingleton.getBeanInModulesContext("GroupsDataSourcePrototype");
                    groupDataSource.setUsersDataSource(usersDataSource);
                    groupDataSource.setUserGroupProvider(userGroupProvider);

                    ExternalContentStoreProvider groupProvider = (ExternalContentStoreProvider) SpringContextSingleton.getBeanInModulesContext("ExternalStoreProviderPrototype");
                    groupProvider.setKey(groupProviderKey);
                    groupProvider.setMountPoint(groupsFolderPath + "/" + PROVIDERS_MOUNT_CONTAINER + "/" + providerKey);
                    groupProvider.setDataSource(groupDataSource);

                    groupDataSource.setContentStoreProvider(groupProvider);

                    groupProvider.start();
                }
            } catch (JahiaInitializationException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    public void unregister(String providerKey) {
        JCRSessionFactory sessionFactory = jcrStoreService.getSessionFactory();
        String userProviderKey = providerKey + ".users";
        JCRStoreProvider provider = sessionFactory.getProviders().get(userProviderKey);
        if (provider != null) {
            provider.stop();
            sessionFactory.removeProvider(providerKey);
        }
        String groupProviderKey = providerKey + ".groups";
        provider = sessionFactory.getProviders().get(groupProviderKey);
        if (provider != null) {
            provider.stop();
            sessionFactory.removeProvider(providerKey);
        }
    }

    public void setJcrStoreService(JCRStoreService jcrStoreService) {
        this.jcrStoreService = jcrStoreService;
    }

    public void setReadOnlyUserProperties(String readOnlyUserProperties) {
        this.readOnlyUserProperties = readOnlyUserProperties;
    }
}
