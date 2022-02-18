/**
 * Copyright 2022 Marco de Booij
 *
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the Licence. You may
 * obtain a copy of the Licence at:
 *
 * http://www.osor.eu/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package eu.debooy.doos.domain;

import java.io.Serializable;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;


/**
 * @author Marco de Booij
 */
public class TaalnaamPK implements Serializable {
  private static final  long  serialVersionUID  = 1L;

	private String iso6392t;
	private Long   taalId;

  public TaalnaamPK() {}

  public TaalnaamPK(Long taalId, String iso6392t) {
    this.iso6392t = iso6392t;
    this.taalId   = taalId;
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof TaalnaamPK)) {
      return false;
    }
    var taalnaamPK = (TaalnaamPK) object;
    return new EqualsBuilder().append(iso6392t, taalnaamPK.iso6392t)
                              .append(taalId, taalnaamPK.taalId)
                              .isEquals();
  }

  public String getIso6392t() {
    return iso6392t;
  }

  public Long getTaalId() {
    return taalId;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(taalId).append(taalId).toHashCode();
  }

  public void setIso6392t(String iso6392t) {
    this.iso6392t = iso6392t.toLowerCase();
  }

  public void setTaalId(Long taalId) {
    this.taalId   = taalId;
  }

  @Override
  public String toString() {
    return new StringBuilder().append("TaalnaamPK")
                              .append(" (taalId=").append(taalId)
                              .append(", iso6392t=").append(iso6392t)
                              .append(")").toString();
  }
}