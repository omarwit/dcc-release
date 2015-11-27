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
package org.icgc.dcc.release.job.document.function;

import static org.icgc.dcc.release.job.document.model.CollectionFieldAccessors.getGeneGeneSetId;
import static org.icgc.dcc.release.job.document.model.CollectionFieldAccessors.getGeneGeneSetType;
import static org.icgc.dcc.release.job.document.model.CollectionFieldAccessors.getGeneGeneSets;
import static org.icgc.dcc.release.job.document.model.CollectionFieldAccessors.removeGeneGeneSets;

import java.util.Map;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;

import org.apache.spark.api.java.function.Function;

import com.fasterxml.jackson.databind.node.ObjectNode;

@RequiredArgsConstructor
public class PivotGeneGeneSets implements Function<ObjectNode, ObjectNode> {

  private static final String GO_TERM_TYPE = "go_term";

  @NonNull
  private final Map<String, String> geneSetOntologies;

  @Override
  public ObjectNode call(ObjectNode gene) throws Exception {
    val geneGeneSets = getGeneGeneSets(gene);
    if (!geneGeneSets.isMissingNode()) {
      // Transform
      for (val geneGeneSet : geneGeneSets) {
        val id = getGeneGeneSetId(geneGeneSet);
        val type = getGeneGeneSetType(geneGeneSet);

        val goTerm = GO_TERM_TYPE.equals(type);
        if (goTerm) {
          val ontology = geneSetOntologies.get(id);
          gene.with(type).withArray(ontology).add(id);
        } else {
          gene.withArray(type).add(id);
        }
      }
    }

    // TODO: This need to only occur if not the "gene" index
    removeGeneGeneSets(gene);

    return gene;
  }

}