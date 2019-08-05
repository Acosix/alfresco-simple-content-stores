FROM ${docker.tests.repositoryBaseImage}
COPY maven ${docker.tests.repositoryWebappPath}

# merge additions to alfresco-global.properties
RUN echo "" >> ${docker.tests.repositoryWebappPath}/../../shared/classes/alfresco-global.properties \
    && echo "#MergeGlobalProperties" >> ${docker.tests.repositoryWebappPath}/../../shared/classes/alfresco-global.properties \
    && sed -i '/#MergeGlobalProperties/r ${docker.tests.repositoryWebappPath}/WEB-INF/classes/alfresco/extension/alfresco-global.addition.properties' ${docker.tests.repositoryWebappPath}/../../shared/classes/alfresco-global.properties