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
package org.icgc.dcc.etl2.job.join.task;

import static org.icgc.dcc.common.core.model.FieldNames.NormalizerFieldNames.NORMALIZER_OBSERVATION_ID;
import static org.icgc.dcc.common.core.model.FieldNames.SubmissionFieldNames.SUBMISSION_ANALYZED_SAMPLE_ID;
import static org.icgc.dcc.etl2.core.util.ObjectNodes.textValue;
import static org.icgc.dcc.etl2.core.util.Tuples.tuple;

import java.util.Map;

import lombok.val;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.icgc.dcc.etl2.core.function.CombineFields;
import org.icgc.dcc.etl2.core.job.FileType;
import org.icgc.dcc.etl2.core.task.GenericTask;
import org.icgc.dcc.etl2.core.task.TaskContext;
import org.icgc.dcc.etl2.job.join.function.KeyAnalysisIdAnalyzedSampleIdField;
import org.icgc.dcc.etl2.job.join.function.KeyDonorMutataionId;
import org.icgc.dcc.etl2.job.join.function.KeyFields;
import org.icgc.dcc.etl2.job.join.function.PairAnalysisIdSampleId;
import org.icgc.dcc.etl2.job.join.function.TransormToOccurrence;
import org.icgc.dcc.etl2.job.join.model.Donor;

import scala.Tuple2;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;

public class ObservationJoinTask extends GenericTask {

  @Override
  public void execute(TaskContext taskContext) {
    val outputFileType = FileType.OBSERVATION;

    val sampleDonorIds = resolveSampleDonorIds(taskContext);
    val sampleSurrogageSampleIds = sampleSurrogageSampleIds(taskContext);

    val ssmM = parseSsmM(taskContext);
    val ssmP = parseSsmP(taskContext);
    val ssmS = parseSsmS(taskContext);

    val output = joinSsm(ssmM, ssmP, ssmS, sampleDonorIds, sampleSurrogageSampleIds);

    writeOutput(taskContext, output, outputFileType);
  }

  private Map<String, String> sampleSurrogageSampleIds(TaskContext taskContext) {
    val samples = parseSample(taskContext);

    return samples
        .mapToPair(s -> tuple(textValue(s, SUBMISSION_ANALYZED_SAMPLE_ID), textValue(s, "_sample_id")))
        .collectAsMap();
  }

  private Map<String, Donor> resolveSampleDonorIds(TaskContext taskContext) {
    val clinical = parseClinical(taskContext);
    val donors = clinical.collect();

    val sampleDonorIds = Maps.<String, Donor> newHashMap();
    for (val donor : donors) {
      val donorId = donor.get("_donor_id").textValue();

      for (val specimen : donor.withArray("specimen")) {
        for (val sample : specimen.withArray("sample")) {
          val sampleId = textValue(sample, SUBMISSION_ANALYZED_SAMPLE_ID);
          val _specimenId = textValue(specimen, "_specimen_id");
          val _sampleId = textValue(sample, "_sample_id");

          sampleDonorIds.put(sampleId, new Donor(donorId, _specimenId, _sampleId));
        }
      }
    }

    return sampleDonorIds;
  }

  private JavaRDD<ObjectNode> parseClinical(TaskContext taskContext) {
    return readInput(taskContext, FileType.CLINICAL);
  }

  private JavaRDD<ObjectNode> parseSsmM(TaskContext taskContext) {
    return readInput(taskContext, FileType.SSM_M);
  }

  private JavaRDD<ObjectNode> parseSsmP(TaskContext taskContext) {
    return readInput(taskContext, FileType.SSM_P_MASKED_SURROGATE_KEY);
  }

  private JavaRDD<ObjectNode> parseSsmS(TaskContext taskContext) {
    return readInput(taskContext, FileType.SSM_S);
  }

  private JavaRDD<ObjectNode> parseSample(TaskContext taskContext) {
    return readInput(taskContext, FileType.SAMPLE_SURROGATE_KEY);
  }

  private static JavaRDD<ObjectNode> joinSsm(JavaRDD<ObjectNode> ssmM, JavaRDD<ObjectNode> ssmP,
      JavaRDD<ObjectNode> ssmS, Map<String, Donor> sampleDonorIds, Map<String, String> sampleSurrogageSampleIds) {
    val ssmPrimarySecondary = joinSsmPrimarySecondary(ssmP, ssmS);

    val observations = ssmPrimarySecondary
        .mapToPair(new PairAnalysisIdSampleId())
        .join(ssmM
            .mapToPair(new KeyAnalysisIdAnalyzedSampleIdField()));

    val donorMutationObservations = observations.groupBy(new KeyDonorMutataionId(sampleDonorIds));

    return donorMutationObservations.map(new TransormToOccurrence(sampleDonorIds, sampleSurrogageSampleIds));
  }

  private static JavaPairRDD<String, Tuple2<ObjectNode, Iterable<ObjectNode>>> joinSsmPrimarySecondary(
      JavaRDD<ObjectNode> ssmP, JavaRDD<ObjectNode> ssmS) {
    return ssmP
        .mapToPair(new KeyFields(NORMALIZER_OBSERVATION_ID))
        .join(
            ssmS.groupBy(new CombineFields(NORMALIZER_OBSERVATION_ID)));
  }

}