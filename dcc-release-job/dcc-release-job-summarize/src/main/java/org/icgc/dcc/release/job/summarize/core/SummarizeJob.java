/*
 * Copyright (c) 2015 The Ontario Institute for Cancer Research. All rights reserved.                             
 *                                                                                                               
 * This program and the accompanying materials are made available under the terms of the GNU Public License v3.0.
 * You should have received a copy of the GNU General Public License along with                                  
 * this program. If not, see <http://www.gnu.org/licenses/>.                                                     
 *                                                                                                               
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY                           
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES                          
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT                           
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,                                
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED                          
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;                               
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER                              
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN                         
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.icgc.dcc.release.job.summarize.core;

import static com.google.common.base.Stopwatch.createStarted;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import org.icgc.dcc.release.core.job.FileType;
import org.icgc.dcc.release.core.job.GenericJob;
import org.icgc.dcc.release.core.job.JobContext;
import org.icgc.dcc.release.core.job.JobType;
import org.icgc.dcc.release.job.summarize.task.DonorSummarizeTask;
import org.icgc.dcc.release.job.summarize.task.FeatureTypeSummarizeTask;
import org.icgc.dcc.release.job.summarize.task.GeneSetSummarizeTask;
import org.icgc.dcc.release.job.summarize.task.GeneSummarizeTask;
import org.icgc.dcc.release.job.summarize.task.MutationSummarizeTask;
import org.icgc.dcc.release.job.summarize.task.ProjectSummarizeTask;
import org.icgc.dcc.release.job.summarize.task.ReleaseSummarizeTask;
import org.icgc.dcc.release.job.summarize.task.ResolveGeneSummaryTask;
import org.icgc.dcc.release.job.summarize.task.ResolveProjectSummaryTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class SummarizeJob extends GenericJob {

  @NonNull
  private final JavaSparkContext sparkContext;

  private static final FileType[] OUTPUT_FILE_TYPES = {
      FileType.DONOR_SUMMARY,
      FileType.GENE_SET_SUMMARY,
      FileType.GENE_SUMMARY,
      FileType.PROJECT_SUMMARY,
      FileType.RELEASE_SUMMARY,
      FileType.MUTATION };

  @Override
  public JobType getType() {
    return JobType.SUMMARIZE;
  }

  @Override
  @SneakyThrows
  public void execute(@NonNull JobContext jobContext) {
    clean(jobContext);
    summarize(jobContext);
  }

  private void clean(JobContext jobContext) {
    delete(jobContext, OUTPUT_FILE_TYPES);
  }

  private void summarize(JobContext jobContext) {
    val watch = createStarted();
    log.info("Executing summary job...");
    jobContext.execute(new GeneSetSummarizeTask());

    val featureTypeSummary = new FeatureTypeSummarizeTask();
    jobContext.execute(featureTypeSummary);
    val donorSummarizeTask = new DonorSummarizeTask(createBroadcast(featureTypeSummary.getProjectFeatureTypeDonors()));
    jobContext.execute(donorSummarizeTask);

    val resolveProjectSummaryTask = new ResolveProjectSummaryTask();
    jobContext.execute(resolveProjectSummaryTask);
    jobContext.execute(new ProjectSummarizeTask(createBroadcast(resolveProjectSummaryTask.getProjectSummaries())));

    val resolveGeneStatsTask = new ResolveGeneSummaryTask();
    jobContext.execute(resolveGeneStatsTask);
    jobContext.execute(new GeneSummarizeTask(createBroadcast(resolveGeneStatsTask.getGeneDonorTypeCounts())),
        new MutationSummarizeTask());

    val donorsCount = donorSummarizeTask.getDonorsCount();
    val liveCount = donorSummarizeTask.getLiveDonorsCount();

    if (donorsCount != 0 || liveCount != 0) {
      jobContext.execute(new ReleaseSummarizeTask(donorsCount, liveCount));
    }
    log.info("Finished executing summary job in {}", watch);
  }

  private <T> Broadcast<T> createBroadcast(T value) {
    return sparkContext.broadcast(value);
  }

}
