var ctxt, cleaner;

ctxt = Packages.org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext();
cleaner = ctxt.getBean('eagerContentStoreCleaner', Packages.org.alfresco.repo.content.cleanup.EagerContentStoreCleaner);
cleaner.setEagerOrphanCleanup(args.eagerCleanup == 'true');