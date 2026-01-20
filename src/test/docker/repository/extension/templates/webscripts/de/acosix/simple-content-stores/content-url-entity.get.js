var ctxt, contentDataDAO;

ctxt = Packages.org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext();
contentDataDAO = ctxt.getBean('contentDataDAO', Packages.org.alfresco.repo.domain.contentdata.ContentDataDAO);
model.entity = contentDataDAO.getContentUrl(args.contentUrl);