package academy.observatory.app;

import static org.junit.Assert.assertTrue;
import java.io.*;

import org.junit.FixMethodOrder;
import org.junit.Test;
import junit.framework.TestCase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.MethodSorters;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONTokener;


import academy.observatory.app.OnixParser;

/**
 * Unit test for simple App.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AppTest 
{
    private static final String OUTPUT_DIR = "/tmp/coki_onix_parser";

    /**
     * Rigorous Test :-)
     */
    @BeforeClass
    public static void setup()
    {
        String pwd = System.getProperty("user.dir");
        String input_dir = pwd + "/test_data";

        OnixParser parser = new OnixParser();
        parser.parseOnix(new File(input_dir), OUTPUT_DIR);

        assertTrue( true );
    }

    @Test
    public void testFullRecords()
    {
        String file = OUTPUT_DIR + "/" + academy.observatory.app.OnixParser.FULL_RECORD_FILE;
        InputStream is = null;

        try {
            is = new FileInputStream(new File(file));
        }
        catch(IOException e) {
        }

        JSONTokener tokener = new JSONTokener(is);
        JSONObject object = new JSONObject(tokener);
        assert(object != null);

        assert(object.getString("RecordSourceName").equals("Academic Observatory"));

        assert(object.getString("RecordSourceType").equals("Bibliographic agency"));
        assert(object.getString("RecordRef").equals("some.test.data"));
        assert(object.getString("CountryOfManufacture").equals("NZ"));
        assert(object.getString("GTIN_13").equals("9780000000000"));
        assert(object.getString("ISBN13").equals("9780000000000"));

        JSONArray languages = object.getJSONArray("Languages");
        Object lang1 = languages.get(0);
        JSONObject lang1o = (JSONObject)lang1;
        assert(lang1o.getString("LanguageCode").equals("eng"));
        assert(lang1o.getString("LanguageRole").equals("Language of text"));

        JSONArray extent = object.getJSONArray("Extent");
        Object ext1 = extent.get(0);
        JSONObject ext1o = (JSONObject)ext1;
        assert(ext1o.getString("ExtentUnit").equals("Pages"));
        assert(ext1o.getLong("ExtentValue") == 100);
        assert(ext1o.getString("ExtentType").equals("Absolute page count"));

        JSONArray titledetails = object.getJSONArray("TitleDetails");
        Object td1 = titledetails.get(0);
        JSONObject td1o = (JSONObject)td1;

        assert(td1o.getString("TitleType").equals("Distinctive title (book); Cover title (serial); Title on item (serial content item or reviewed resource)"));

        JSONArray te = td1o.getJSONArray("TitleElements");
        Object te1 = te.get(0);
        JSONObject te1o = (JSONObject)te1;
        assert(te1o.getString("TitleWithoutPrefix").equals("TestTitle"));
        assert(te1o.getLong("SequenceNumber")==1);
        assert(te1o.getString("TitleWithoutPrefix_TextCaseFlags").equals("Sentence case"));
        assert(te1o.getString("TitleElementLevel").equals("Product"));
        assert(te1o.getString("Subtitle").equals("Subtitle text"));

        Object td2 = titledetails.get(1);
        JSONObject td2o = (JSONObject)td2;
        assert(td2o.getString("TitleType").equals("Distributor\u2019s title"));
        JSONArray te2a = td2o.getJSONArray("TitleElements");
        Object te2 = te2a.get(0);
        JSONObject te2o = (JSONObject)te2;
        assert(te2o.getString("TitleText").equals("TestTitle Book"));
        assert(te2o.getString("TitleElementLevel").equals("Product"));

        JSONArray pubdates = object.getJSONArray("PublishingDates");
        Object pubdate1 = pubdates.get(0);
        JSONObject pd1 = (JSONObject)pubdate1;
        assert(pd1.getString("Date").equals("20200101"));
        assert(pd1.getString("PublishingDateRole").equals("Publication date"));
        Object pubdate2 = pubdates.get(1);
        JSONObject pd2 = (JSONObject)pubdate2;
        assert(pd2.getString("Date").equals("2020"));
        assert(pd2.getString("PublishingDateRole").equals("Date of first publication"));
        Object pubdate3 = pubdates.get(2);
        JSONObject pd3 = (JSONObject)pubdate3;
        assert(pd3.getString("Date").equals("2020"));
        assert(pd3.getString("PublishingDateRole").equals("Date of first publication in original language"));

        JSONArray contributors = object.getJSONArray("Contributors");
        Object contrib1 = contributors.get(0);
        JSONObject c1o = (JSONObject)contrib1;
        assert(c1o.getString("NamesBeforeKey").equals("MyName"));
        assert(c1o.getString("ISNI").equals("0000000111111111"));
        assert(c1o.getLong("SequenceNumber")==1);
        assert(c1o.getString("KeyNames").equals("TestKeyName"));
        assert(c1o.getString("PersonName").equals("MyPersonName"));

        JSONArray croles = c1o.getJSONArray("Roles");
        Object cr1 = croles.get(0);
        String cr1o = (String)cr1;
        assert(cr1.equals("By (author)"));
        assert(c1o.getString("Proprietary").equals("1111"));

        JSONArray cr1b = c1o.getJSONArray("BiographicalNotes");
        Object cr1b1 = cr1b.get(0);
        JSONObject cr1b1o = (JSONObject)cr1b1;
        assert(cr1b1o.getString("Note").equals("Some note."));
        assert(cr1b1o.getString("TextFormat").equals("XHTML"));

        Object contrib2 = contributors.get(1);
        JSONObject c2o = (JSONObject)contrib2;
        assert(c2o.getString("NamesBeforeKey").equals("BeforeName"));
        assert(c2o.getString("ISNI").equals("0000000111111111"));
        assert(c2o.getLong("SequenceNumber")==2);
        assert(c2o.getString("KeyNames").equals("KeyName"));

        JSONArray croles2 = c2o.getJSONArray("Roles");
        Object cr2 = croles2.get(0);
        String cr2o = (String)cr2;
        assert(cr2o.equals("By (author)"));
        assert(c2o.getString("Proprietary").equals("7422"));

        JSONArray cr2b = c2o.getJSONArray("BiographicalNotes");
        Object cr2b1 = cr2b.get(0);
        JSONObject cr2b1o = (JSONObject)cr2b1;
        assert(cr2b1o.getString("Note").equals("Some note."));
        assert(cr2b1o.getString("TextFormat").equals("XHTML"));

        JSONArray pubs = object.getJSONArray("Publishers");
        Object pubs1 = pubs.get(0);
        JSONObject pubs1o = (JSONObject)pubs1;
        assert(pubs1o.getString("PublishingRole").equals("Publisher"));
        assert(pubs1o.getString("PublisherName").equals("Academic Observatory"));

        JSONArray pubs1web = pubs1o.getJSONArray("Websites");
        Object pubs1web1 = pubs1web.get(0);
        JSONObject pubs1web1o = (JSONObject)pubs1web1;

        assert(pubs1web1o.getString("WebsiteRole").equals("Publisher\u2019s corporate website"));

        JSONArray pubs1web1links = pubs1web1o.getJSONArray("WebsiteLinks");
        Object pubs1web1links1 = pubs1web1links.get(0);
        String pubs1web1links1o = (String)pubs1web1links1;
        assert(pubs1web1links1o.equals("http://observatory.academic"));

        JSONArray citpub = object.getJSONArray("CityOfPublications");
        Object citpub1 = citpub.get(0);
        String citpub1o = (String)citpub1;
        assert(citpub1o.equals("Auckland"));

        JSONArray imprint = object.getJSONArray("Imprints");
        Object imprint1 = imprint.get(0);
        JSONObject imprint1o = (JSONObject)imprint1;
        assert(imprint1o.getString("ImprintName").equals("Academic Observatory"));

        JSONArray textcontent = object.getJSONArray("TextContent");
        Object txtcont1 = textcontent.get(0);
        JSONObject txtcont1o = (JSONObject)txtcont1;
        JSONArray txtcont1t = txtcont1o.getJSONArray("Text");
        Object txtcont1t1 = txtcont1t.get(0);
        String txtcont1t1o = (String)txtcont1t1;
        assert(txtcont1t1o.equals("Some text."));
        assert(txtcont1o.getString("TextType").equals("Short description/annotation"));

        Object txtcont2 = textcontent.get(1);
        JSONObject txtcont2o = (JSONObject)txtcont2;
        JSONArray txtcont2t = txtcont2o.getJSONArray("Text");
        Object txtcont2t1 = txtcont2t.get(0);
        String txtcont2t1o = (String)txtcont2t1;
        assert(txtcont2t1o.equals("More text."));
        assert(txtcont2o.getString("TextType").equals("Description"));

        Object txtcont3 = textcontent.get(2);
        JSONObject txtcont3o = (JSONObject)txtcont3;
        JSONArray txtcont3t = txtcont3o.getJSONArray("Text");
        Object txtcont3t1 = txtcont3t.get(0);
        String txtcont3t1o = (String)txtcont3t1;
        assert(txtcont3t1o.equals("TOC."));
        assert(txtcont3o.getString("TextType").equals("Table of contents"));

        JSONArray subjects = object.getJSONArray("Subjects");
        Object subjects1 = subjects.get(0);
        JSONObject subjects1o = (JSONObject)subjects1;
        assert(subjects1o.getBoolean("MainSubject") == true);
        assert(subjects1o.getString("SubjectSchemeIdentifier").equals("BISAC_Subject_Heading"));
        assert(subjects1o.getString("SubjectCode").equals("BISAC HEADING"));
        assert(subjects1o.getString("SubjectSchemeVersion").equals("1.0"));

        Object subjects2 = subjects.get(1);
        JSONObject subjects2o = (JSONObject)subjects2;
        assert(subjects2o.getBoolean("MainSubject") == false);
        assert(subjects2o.getString("SubjectSchemeIdentifier").equals("BIC_subject_category"));
        assert(subjects2o.getString("SubjectCode").equals("BIC CATEGORY"));
        assert(subjects2o.getString("SubjectSchemeVersion").equals("1.1"));

        Object subjects3 = subjects.get(2);
        JSONObject subjects3o = (JSONObject)subjects3;
        assert(subjects3o.getBoolean("MainSubject") == false);
        assert(subjects3o.getString("SubjectSchemeIdentifier").equals("Keywords"));
        assert(subjects3o.getString("SubjectCode").equals("KEYWORD"));
        assert(subjects3o.getString("SubjectSchemeVersion").equals("1.2"));
    }

    @Test
    public void testUpdateRecords()
    {
        String file = OUTPUT_DIR + "/" + academy.observatory.app.OnixParser.UPDATE_RECORD_FILE;
        InputStream is = null;

        try {
            is = new FileInputStream(new File(file));
        }
        catch(IOException e) {
        }

        JSONTokener tokener = new JSONTokener(is);
        JSONObject object = new JSONObject(tokener);
        assert(object != null);

        assert(object.getString("RecordSourceName").equals("Academic Observatory"));

        assert(object.getString("RecordRef").equals("some.test.data"));
        assert(object.getString("ISBN13").equals("9780000000000"));
        assert(object.getString("RecordSourceType").equals("Bibliographic agency"));
        assert(object.getString("GTIN_13").equals("9781111111111"));
    }

    @Test
    public void testDeleteRecords()
    {
        String file = OUTPUT_DIR + "/" + academy.observatory.app.OnixParser.DELETE_RECORD_FILE;
        InputStream is = null;

        try {
            is = new FileInputStream(new File(file));
        }
        catch(IOException e) {
        }

        JSONTokener tokener = new JSONTokener(is);
        JSONObject object = new JSONObject(tokener);
        assert(object != null);

        assert(object.getString("RecordSourceName").equals("Academic Observatory"));

        assert(object.getString("RecordRef").equals("some.test.data"));
        assert(object.getString("ISBN13").equals("9780000000000"));
        assert(object.getString("RecordSourceType").equals("Bibliographic agency"));
        assert(object.getString("GTIN_13").equals("9780000000000"));
    }

    @AfterClass
    public static void deleteTestFolder()
    {
        File output_directory = new File(OUTPUT_DIR);
        // deleteFiles(output_directory);
    }

    public static void deleteFiles(File file)
    {
        for(File subFile : file.listFiles()) {
            if(subFile.isDirectory()) {
                deleteFiles(subFile);
            } else {
                subFile.delete();
            }
        }

        file.delete();
    }
}
