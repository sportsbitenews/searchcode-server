/*
 * Copyright (c) 2016 Boyter Online Services
 *
 * Use of this software is governed by the Fair Source License included
 * in the LICENSE.TXT file, but will be eventually open under GNU General Public License Version 3
 * see the README.md for when this clause will take effect
 *
 * Version 1.3.11
 */

package com.searchcode.app.jobs.enqueue;

import com.searchcode.app.dao.Repo;
import com.searchcode.app.model.RepoResult;
import com.searchcode.app.service.IIndexService;
import com.searchcode.app.service.IndexService;
import com.searchcode.app.service.Singleton;
import com.searchcode.app.util.LoggerWrapper;
import com.searchcode.app.util.UniqueRepoQueue;
import org.quartz.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Responsible for adding all of the repositories inside the database into the queues. There will be a queue
 * for GIT and SVN or any other repository added.
 * TODO add logic to test that the right queue has things added to it
 */
@PersistJobDataAfterExecution
@DisallowConcurrentExecution
public class EnqueueRepositoryJob implements Job {

    private final IndexService indexService;
    private final LoggerWrapper logger;
    private final Repo repo;

    public EnqueueRepositoryJob() {
        this.indexService = Singleton.getIndexService();
        this.repo = Singleton.getRepo();
        this.logger = Singleton.getLogger();
    }

    public void execute(JobExecutionContext context) throws JobExecutionException {
        if (this.indexService.shouldPause(IIndexService.JobType.REPO_ADDER)) {
            return;
        }

        try {
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

            UniqueRepoQueue repoGitQueue = Singleton.getUniqueGitRepoQueue();
            UniqueRepoQueue repoSvnQueue = Singleton.getUniqueSvnRepoQueue();

            // Get all of the repositories and enqueue them
            List<RepoResult> repoResultList = this.repo.getAllRepo();

            // Filter out those queued to be deleted
            List<String> persistentDelete = Singleton.getDataService().getPersistentDelete();
            List<RepoResult> collect = repoResultList.stream()
                                                     .filter(x -> !persistentDelete.contains(x.getName()))
                                                     .collect(Collectors.toList());

            this.logger.info("Adding repositories to be indexed. " + collect.size());

            for (RepoResult rr: collect) {
                switch (rr.getScm().toLowerCase()) {
                    case "git":
                        this.logger.info("Adding to GIT queue " + rr.getName() + " " + rr.getScm());
                        repoGitQueue.add(rr);
                        break;
                    case "svn":
                        this.logger.info("Adding to SVN queue " + rr.getName() + " " + rr.getScm());
                        repoSvnQueue.add(rr);
                        break;
                    default:
                        break;
                }
            }
        }
        catch (Exception ignored) {}
    }
}
