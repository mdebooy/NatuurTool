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

import eu.debooy.doosutils.access.Bestand;
import eu.debooy.doosutils.exception.BestandException;
import eu.debooy.doosutils.test.BatchTest;
import eu.debooy.doosutils.test.DoosUtilsTestConstants;
import eu.debooy.doosutils.test.VangOutEnErr;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * @author Marco de Booij
 */
public class IocNamenTest extends BatchTest {
  protected static final  ClassLoader CLASSLOADER =
      IocNamenTest.class.getClassLoader();

  private static final  String  BST_CSV   = "MultilingIOC.csv";
  private static final  String  BST_JSON  = "MultilingIOC.json";

  @AfterClass
  public static void afterClass() {
//    verwijderBestanden(TEMP + File.separator,
//                       new String[] {BST_CSV, BST_JSON});
  }

  @BeforeClass
  public static void beforeClass() throws BestandException {
    Locale.setDefault(new Locale("nl"));
    resourceBundle  = ResourceBundle.getBundle("ApplicatieResources",
                                               Locale.getDefault());

    try {
      kopieerBestand(CLASSLOADER, BST_CSV,
                     TEMP + File.separator + BST_CSV);
    } catch (IOException e) {
      System.out.println(e.getLocalizedMessage());
      throw new BestandException(e);
    }
  }

  @Test
  public void testCsv() throws BestandException {
    String[]  args      = new String[] {
      "--" + NatuurTools.PAR_IOCBESTAND + "=" + BST_CSV,
      "--" + NatuurTools.PAR_TALEN + "=en,af,ca,zh,z,hr,cs,da,nl,et,fi,fr,de,hu,is,id,it,ja,lv,lt,se,no,pl,pt,ru,sk,sl,es,sv,th,uk",
      "--invoerdir=" + TEMP};

    VangOutEnErr.execute(IocNamen.class,
                         DoosUtilsTestConstants.CMD_EXECUTE, args, out, err);

    assertEquals("CSV - helptekst", 18, out.size());
    assertEquals("CSV - fouten", 0, err.size());
    assertEquals("CSV - 12", "33", out.get(11).split(":")[1].trim());
    assertEquals("CSV - 13", "2",  out.get(12).split(":")[1].trim());
    assertEquals("CSV - 14", "3",  out.get(13).split(":")[1].trim());
    assertEquals("CSV - 15", "6",  out.get(14).split(":")[1].trim());
    assertTrue("CSV - equals",
        Bestand.equals(
            Bestand.openInvoerBestand(TEMP + File.separator
                                      + BST_JSON),
            Bestand.openInvoerBestand(IocNamenTest.class.getClassLoader(),
                                      BST_JSON)));
  }

  @Test
  public void testLeeg() {
    String[]  args      = new String[] {};

    VangOutEnErr.execute(IocNamen.class, "execute", args, out, err);

    assertEquals("Zonder parameters - helptekst", 27, out.size());
    assertEquals("Zonder parameters - fouten", 1, err.size());
  }
}
