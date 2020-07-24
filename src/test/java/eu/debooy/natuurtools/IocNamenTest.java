/*
 * Copyright (c) 2020 Marco de Booij
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the Licence. You may
 * obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package eu.debooy.natuurtools;

import eu.debooy.doosutils.exception.BestandException;
import eu.debooy.doosutils.test.BatchTest;
import eu.debooy.doosutils.test.VangOutEnErr;
import java.util.Locale;
import java.util.ResourceBundle;
import static org.junit.Assert.assertEquals;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * @author Marco de Booij
 */
public class IocNamenTest extends BatchTest {
  protected static final  ClassLoader CLASSLOADER =
      IocNamenTest.class.getClassLoader();

  @BeforeClass
  public static void beforeClass() throws BestandException {
    Locale.setDefault(new Locale("nl"));
    resourceBundle  = ResourceBundle.getBundle("ApplicatieResources",
                                               Locale.getDefault());
  }

  @Test
  public void testLeeg() {
    String[]  args      = new String[] {};

    VangOutEnErr.execute(IocNamen.class, "execute", args, out, err);

    assertEquals("Zonder parameters - helptekst", 27, out.size());
    assertEquals("Zonder parameters - fouten", 0, 0);
  }
}
