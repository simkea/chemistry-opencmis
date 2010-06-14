/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.chemistry.opencmis.inmemory.storedobj.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.chemistry.opencmis.commons.data.RepositoryInfo;
import org.apache.chemistry.opencmis.commons.definitions.TypeDefinition;
import org.apache.chemistry.opencmis.commons.definitions.TypeDefinitionContainer;
import org.apache.chemistry.opencmis.commons.enums.CapabilityAcl;
import org.apache.chemistry.opencmis.commons.enums.CapabilityChanges;
import org.apache.chemistry.opencmis.commons.enums.CapabilityContentStreamUpdates;
import org.apache.chemistry.opencmis.commons.enums.CapabilityJoin;
import org.apache.chemistry.opencmis.commons.enums.CapabilityQuery;
import org.apache.chemistry.opencmis.commons.enums.CapabilityRenditions;
import org.apache.chemistry.opencmis.commons.exceptions.CmisInvalidArgumentException;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.AbstractTypeDefinition;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.BindingsObjectFactoryImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.RepositoryCapabilitiesImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.RepositoryInfoImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.TypeDefinitionContainerImpl;
import org.apache.chemistry.opencmis.commons.spi.BindingsObjectFactory;
import org.apache.chemistry.opencmis.inmemory.RepositoryInfoCreator;
import org.apache.chemistry.opencmis.inmemory.TypeCreator;
import org.apache.chemistry.opencmis.inmemory.TypeManager;
import org.apache.chemistry.opencmis.inmemory.TypeManagerImpl;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.ObjectStore;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.StoreManager;

/**
 * factory to create objects that are stored in the InMemory store
 * 
 * @author Jens
 */
public class StoreManagerImpl implements StoreManager {

    protected BindingsObjectFactory fObjectFactory;
    protected RepositoryInfo fRepositoryInfo;

    /**
     * map from repository id to a type manager
     */
    private Map<String, TypeManagerImpl> fMapRepositoryToTypeManager = new HashMap<String, TypeManagerImpl>();

    /**
     * map from repository id to a object store
     */
    private Map<String, ObjectStore> fMapRepositoryToObjectStore = new HashMap<String, ObjectStore>();

    public ObjectStoreImpl getStore(String repositoryId) {
        return (ObjectStoreImpl) fMapRepositoryToObjectStore.get(repositoryId);
    }

    public StoreManagerImpl() {
        fObjectFactory = new BindingsObjectFactoryImpl();
    }

    public List<String> getAllRepositoryIds() {
        Set<String> repIds = fMapRepositoryToObjectStore.keySet();
        List<String> result = new ArrayList<String>();
        result.addAll(repIds);
        return result;
    }

    public void initRepository(String repositoryId) {
        fMapRepositoryToObjectStore.put(repositoryId, new ObjectStoreImpl(repositoryId));
        fMapRepositoryToTypeManager.put(repositoryId, new TypeManagerImpl());
    }

    public void createAndInitRepository(String repositoryId, String typeCreatorClassName) {
        if (fMapRepositoryToObjectStore.containsKey(repositoryId)
                || fMapRepositoryToTypeManager.containsKey(repositoryId))
            throw new RuntimeException("Cannot add repository, repository " + repositoryId + " already exists.");

        fMapRepositoryToObjectStore.put(repositoryId, new ObjectStoreImpl(repositoryId));
        fMapRepositoryToTypeManager.put(repositoryId, new TypeManagerImpl());

        // initialize the type system:
        initTypeSystem(repositoryId, typeCreatorClassName);
    }

    public ObjectStore getObjectStore(String repositoryId) {
        return fMapRepositoryToObjectStore.get(repositoryId);
    }

    public BindingsObjectFactory getObjectFactory() {
        return fObjectFactory;
    }

    public TypeDefinitionContainer getTypeById(String repositoryId, String typeId) {
        TypeManager typeManager = fMapRepositoryToTypeManager.get(repositoryId);
        if (null == typeManager)
            throw new RuntimeException("Unknown repository " + repositoryId);

        return typeManager.getTypeById(typeId);
    }

    public TypeDefinitionContainer getTypeById(String repositoryId, String typeId, boolean includePropertyDefinitions,
            int depth) {
        TypeManager typeManager = fMapRepositoryToTypeManager.get(repositoryId);
        if (null == typeManager)
            throw new RuntimeException("Unknown repository " + repositoryId);

        TypeDefinitionContainer tc = typeManager.getTypeById(typeId);
        List<TypeDefinitionContainer> result = null;

        if (tc != null) {
            if (depth == -1) {
                result = tc.getChildren();
                if (!includePropertyDefinitions)
                    cloneTypeList(depth - 1, false, result);
            } else if (depth == 0 || depth < -1)
                throw new CmisInvalidArgumentException("illegal depth value: " + depth);
            else {
                result = tc.getChildren();
                cloneTypeList(depth - 1, includePropertyDefinitions, result);
            }
        }
        return tc;
    }

    public Collection<TypeDefinitionContainer> getTypeDefinitionList(String repositoryId,
            boolean includePropertyDefinitions) {
        Collection<TypeDefinitionContainer> result;
        TypeManager typeManager = fMapRepositoryToTypeManager.get(repositoryId);
        if (null == typeManager)
            throw new RuntimeException("Unknown repository " + repositoryId);
        Collection<TypeDefinitionContainer> typeColl = typeManager.getTypeDefinitionList();
        if (includePropertyDefinitions) {
            result = typeColl;
        } else {
            result = new ArrayList<TypeDefinitionContainer>(typeColl);
            // copy list and omit properties
            for (TypeDefinitionContainer c : result) {
                AbstractTypeDefinition td = ((AbstractTypeDefinition) c.getTypeDefinition()).clone();
                TypeDefinitionContainerImpl tdc = new TypeDefinitionContainerImpl(td);
                tdc.setChildren(c.getChildren());
                td.setPropertyDefinitions(null);
            }
        }
        return result;
    }

    public Map<String, TypeDefinitionContainer> getTypeDefinitionMap(String repositoryId) {
        return null;
    }

    public List<TypeDefinitionContainer> getRootTypes(String repositoryId) {
        TypeManager typeManager = fMapRepositoryToTypeManager.get(repositoryId);
        if (null == typeManager)
            throw new RuntimeException("Unknown repository " + repositoryId);
        List<TypeDefinitionContainer> rootTypes = typeManager.getRootTypes();

        return rootTypes;
    }

    public RepositoryInfo getRepositoryInfo(String repositoryId) {
        ObjectStore sm = fMapRepositoryToObjectStore.get(repositoryId);
        if (null == sm)
            return null;

        RepositoryInfo repoInfo = createDefaultRepositoryInfo(repositoryId);

        return repoInfo;
    }

    public void clearTypeSystem(String repositoryId) {
        TypeManagerImpl typeManager = fMapRepositoryToTypeManager.get(repositoryId);
        if (null == typeManager)
            throw new RuntimeException("Unknown repository " + repositoryId);

        typeManager.clearTypeSystem();
    }

    public void initRepositoryInfo(String repositoryId, String repoInfoCreatorClassName) {
        RepositoryInfoCreator repoCreator = null;

        if (repoInfoCreatorClassName != null) {
            Object obj = null;
            try {
                obj = Class.forName(repoInfoCreatorClassName).newInstance();
            } catch (InstantiationException e) {
                throw new RuntimeException(
                        "Illegal class to create type system, must implement RepositoryInfoCreator interface.", e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(
                        "Illegal class to create type system, must implement RepositoryInfoCreator interface.", e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(
                        "Illegal class to create type system, must implement RepositoryInfoCreator interface.", e);
            }

            if (obj instanceof RepositoryInfoCreator) {
                repoCreator = (RepositoryInfoCreator) obj;
                fRepositoryInfo = repoCreator.createRepositoryInfo();
            } else
                throw new RuntimeException(
                        "Illegal class to create repository info, must implement RepositoryInfoCreator interface.");
        } else {
            // create a default repository info
            createDefaultRepositoryInfo(repositoryId);
        }
    }

    public List<TypeDefinition> initTypeSystem(String typeCreatorClassName) {

        List<TypeDefinition> typesList = null;

        if (typeCreatorClassName != null) {
            Object obj = null;
            TypeCreator typeCreator = null;

            try {
                obj = Class.forName(typeCreatorClassName).newInstance();
            } catch (InstantiationException e) {
                throw new RuntimeException(
                        "Illegal class to create type system, must implement TypeCreator interface.", e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(
                        "Illegal class to create type system, must implement TypeCreator interface.", e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(
                        "Illegal class to create type system, must implement TypeCreator interface.", e);
            }

            if (obj instanceof TypeCreator)
                typeCreator = (TypeCreator) obj;
            else
                throw new RuntimeException("Illegal class to create type system, must implement TypeCreator interface.");

            // retrieve the list of available types from the configured class.
            // test
            typesList = typeCreator.createTypesList();
        }

        return typesList;
    }

    private void initTypeSystem(String repositoryId, String typeCreatorClassName) {

        List<TypeDefinition> typeDefs = null;
        TypeManagerImpl typeManager = fMapRepositoryToTypeManager.get(repositoryId);
        if (null == typeManager)
            throw new RuntimeException("Unknown repository " + repositoryId);

        if (null != typeCreatorClassName)
            typeDefs = initTypeSystem(typeCreatorClassName);

        typeManager.initTypeSystem(typeDefs);
    }

    private RepositoryInfo createDefaultRepositoryInfo(String repositoryId) {
        ObjectStore objStore = getObjectStore(repositoryId);
        String rootFolderId = objStore.getRootFolder().getId();
        // repository info
        RepositoryInfoImpl repoInfo;
        repoInfo = new RepositoryInfoImpl();
        repoInfo.setRepositoryId(repositoryId == null ? "inMem" : repositoryId);
        repoInfo.setRepositoryName("InMemory Repository");
        repoInfo.setRepositoryDescription("InMemory Test Repository");
        repoInfo.setCmisVersionSupported("1.0");
        repoInfo.setRepositoryCapabilities(null);
        repoInfo.setRootFolder(rootFolderId);
        repoInfo.setPrincipalAnonymous("anonymous");
        repoInfo.setPrincipalAnyone("anyone");
        repoInfo.setThinClientUri(null);
        repoInfo.setChangesIncomplete(Boolean.TRUE);
        repoInfo.setChangesOnType(null);
        repoInfo.setLatestChangeLogToken(null);
        repoInfo.setVendorName("OpenCMIS");
        repoInfo.setProductName("OpenCMIS InMemory-Server");
        repoInfo.setProductVersion("0.1");

        // set capabilities
        RepositoryCapabilitiesImpl caps = new RepositoryCapabilitiesImpl();
        caps.setAllVersionsSearchable(false);
        caps.setCapabilityAcl(CapabilityAcl.NONE);
        caps.setCapabilityChanges(CapabilityChanges.PROPERTIES); // just for
        // testing
        caps.setCapabilityContentStreamUpdates(CapabilityContentStreamUpdates.PWCONLY);
        caps.setCapabilityJoin(CapabilityJoin.NONE);
        caps.setCapabilityQuery(CapabilityQuery.METADATAONLY); // just for
        // testing
        caps.setCapabilityRendition(CapabilityRenditions.NONE);
        caps.setIsPwcSearchable(false);
        caps.setIsPwcUpdatable(true);
        caps.setSupportsGetDescendants(true);
        caps.setSupportsGetFolderTree(true);
        caps.setSupportsMultifiling(true);
        caps.setSupportsUnfiling(true);
        caps.setSupportsVersionSpecificFiling(false);
        repoInfo.setRepositoryCapabilities(caps);

        // AclCapabilitiesDataImpl aclCaps = new AclCapabilitiesDataImpl();
        // aclCaps.setACLPropagation(AclPropagation.REPOSITORYDETERMINED);
        // aclCaps.setPermissionDefinitionData(null);
        // aclCaps.setPermissionMappingData(null);
        // repoInfo.setACLCapabilities(aclCaps);
        repoInfo.setAclCapabilities(null);
        fRepositoryInfo = repoInfo;
        return repoInfo;
    }

    /**
     * traverse tree and replace each need node with a clone. remove properties
     * on clone if requested, cut children of clone if depth is exceeded.
     * 
     * @param depth
     * @param types
     */
    private void cloneTypeList(int depth, boolean includePropertyDefinitions, List<TypeDefinitionContainer> types) {

        ListIterator<TypeDefinitionContainer> it = types.listIterator();
        while (it.hasNext()) {
            TypeDefinitionContainer tdc = it.next();
            AbstractTypeDefinition td = ((AbstractTypeDefinition) tdc.getTypeDefinition()).clone();
            if (!includePropertyDefinitions)
                td.setPropertyDefinitions(null);
            TypeDefinitionContainerImpl tdcClone = new TypeDefinitionContainerImpl(td);
            if (depth > 0) {
                ArrayList<TypeDefinitionContainer> children = new ArrayList<TypeDefinitionContainer>(tdc.getChildren()
                        .size());
                children.addAll(tdc.getChildren());
                tdcClone.setChildren(children);
                cloneTypeList(depth - 1, includePropertyDefinitions, children);
            }
            it.set(tdcClone);
        }
    }

    public TypeManager getTypeManager(String repositoryId) {
        TypeManager typeManager = fMapRepositoryToTypeManager.get(repositoryId);
        return typeManager;
    }

}
