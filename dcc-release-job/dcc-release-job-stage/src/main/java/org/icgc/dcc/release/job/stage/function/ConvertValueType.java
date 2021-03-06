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
package org.icgc.dcc.release.job.stage.function;

import static java.lang.Double.parseDouble;
import static java.lang.Long.parseLong;

import java.io.Serializable;
import java.util.Map;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;

import org.apache.spark.api.java.function.Function;
import org.icgc.dcc.common.core.model.ValueType;
import org.icgc.dcc.release.core.submission.SubmissionFileSchema;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;

@RequiredArgsConstructor
public class ConvertValueType implements Function<ObjectNode, ObjectNode>, Serializable {

  @NonNull
  private final Map<String, ValueType> fieldTypes = Maps.newHashMap();

  public ConvertValueType(@NonNull SubmissionFileSchema schema) {
    for (val field : schema.getFields()) {
      fieldTypes.put(field.getName(), field.getType());
    }
  }

  @Override
  public ObjectNode call(ObjectNode row) throws Exception {
    for (val entry : fieldTypes.entrySet()) {
      val fieldName = entry.getKey();
      val fieldType = entry.getValue();

      val value = row.path(fieldName).textValue();
      if (value == null) {
        continue;
      }

      try {
        if (fieldType == ValueType.DECIMAL) {
          row.put(fieldName, parseDouble(value));
        } else if (fieldType == ValueType.INTEGER) {
          row.put(fieldName, parseLong(value));
        } else {
          row.put(fieldName, value);
        }
      } catch (Exception e) {
        throw new IllegalArgumentException("Could not convert value " + "'" + value + "' in field " + fieldName
            + "' with type " + fieldType, e);
      }
    }

    return row;
  }

}