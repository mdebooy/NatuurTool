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

import eu.debooy.doosutils.test.BatchTest;
import eu.debooy.doosutils.test.VangOutEnErr;
import java.io.File;
import java.util.Locale;
import java.util.ResourceBundle;
import static org.junit.Assert.assertEquals;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * @author Marco de Booij
 */
public class TaxaImportTest extends BatchTest {
  protected static final  ClassLoader CLASSLOADER =
      TaxaImportTest.class.getClassLoader();

  private static final  String  BST_JSON        = "MultilingIOC.json";
  private static final  String  PAR_JSONBESTAND = "jsonbestand";

  @BeforeClass
  public static void beforeClass() {
    Locale.setDefault(new Locale("nl"));
    resourceBundle  = ResourceBundle.getBundle("ApplicatieResources",
                                               Locale.getDefault());
  }

  @Test
  public void testBestandMetDirectory() {
    String[]  args      = new String[] {"--" + PAR_JSONBESTAND
                                         + "=" + TEMP + File.separator
                                         + BST_JSON,
                                        "--" + NatuurTools.PAR_TAXAROOT + "=kl,Aves",
                                        "--dburl=localhost:5432/db",
                                        "--dbuser=dbuser",
                                        "--invoerdir=" + TEMP};

    VangOutEnErr.execute(TaxaImport.class, "execute", args, out, err);

    assertEquals("Bestand met directory - helptekst", 30, out.size());
    assertEquals("Bestand met directory - fouten", 2, err.size());
  }

  @Test
  public void testLeeg() {
    String[]  args      = new String[] {};

    VangOutEnErr.execute(TaxaImport.class, "execute", args, out, err);

    assertEquals("Zonder parameters - helptekst", 30, out.size());
    assertEquals("Zonder parameters - fouten", 1, err.size());
  }
}
