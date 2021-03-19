/* Copyright 2020 Curtin University

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author: Tuan Chien
*/

/*
 * ONIX for Books specifications can be found at:
 * https://www.editeur.org/83/Overview/
 */

package academy.observatory.app;

import java.io.File;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import java.util.*;

import org.json.JSONObject;
import org.json.JSONArray;

import com.tectonica.jonix.*;
import com.tectonica.jonix.common.codelist.*;
import com.tectonica.jonix.common.*;
import com.tectonica.jonix.common.struct.*;
import com.tectonica.jonix.onix3.*;
import static com.tectonica.jonix.tabulate.JonixDelimitedWriter.toDelimitedFile;
import com.tectonica.jonix.unify.*;

/**
 * ONIX for Books message parsing for the Academic Observatory telescope.
 * Creates a command line program that reads a directory of ONIX XML messages
 * and outputs them into full record, update record, and deletion record files
 * for further processing by an AO telescope.
 */
public class OnixParser {
	public static final String FULL_RECORD_FILE = "full.jsonl";
	public static final String UPDATE_RECORD_FILE = "update.jsonl";
	public static final String DELETE_RECORD_FILE = "delete.jsonl";

	/**
	 * Processes a directory of ONIX messages, and writes out full.json,
	 * updates.json, deletes.json to the output directory.
	 * 
	 * @param args Command line arguments (required). args[0] is a directory
	 *             containing ONIX XML files to process. args[1] is the output
	 *             directory. arg[2] is the data source name (no spaces) to use as part of an internal id.
	 */
	public static void main(String[] args) {
		if (args.length < 3) {
			throw new RuntimeException(
					"Requires the directory containing ONIX xml files as the first argument, the output directory as the second argument, and a data source name as the third argument.");
		}

		File input_directory = new File(args[0]);
		String output_directory = args[1];
		String data_src = args[2];
		parseOnix(input_directory, output_directory, data_src);
	}

	/**
	 * Parse ONIX records. Write out records to full/update/delete json files.
	 * @param input_directory File object for input directory.
	 * @param output_directory String object for output directory.
	 * @param data_src Data source name.
	 */
	public static void parseOnix(File input_directory, String output_directory, String data_src) {
		create_directory_if_missing(output_directory);

		try {
			JonixRecords records = Jonix.source(input_directory, "*.xml", false).onSourceStart(src -> {
				System.out.println("Processing " + src.onixVersion() + " file: " + src.sourceName());

				// We're only going to process ONIX3. If ONIX2 is required later, use
				// JonixUnifier or process it separately.
				if (src.onixVersion() != OnixVersion.ONIX3) {
					throw new RuntimeException("ONIX2 message received. We are only processing ONIX3");
				}
			}).onSourceEnd(src -> {
				System.out.println("Processed records: " + src.productsProcessedCount());
			}).configure("jonix.stream.failOnInvalidFile", Boolean.FALSE);

			// CSV serialisation
			// File targetFile = new File("/tmp/test.csv");
			// int recordsWritten =
			// records.streamUnified().collect(toDelimitedFile(targetFile,',',BaseTabulation.ALL));

			// JSON serialisation
			processRecords(records, output_directory, data_src);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create directory if missing.
	 * @param dir_path Directory path
	 */
	private static void create_directory_if_missing(String dir_path) {
		File directory = new File(dir_path);
		if(!directory.exists()) {
			directory.mkdirs();
		}
	}

	/**
	 * Process ONIX records identified by Jonix.
	 * 
	 * @param records    List of Jonix records.
	 * @param output_dir Output directory.
	 * @param data_src Data source name.
	 */
	private static void processRecords(JonixRecords records, String output_dir, String data_src) {
		List<JSONObject> full_list = new ArrayList<JSONObject>();
		List<JSONObject> update_list = new ArrayList<JSONObject>();
		List<JSONObject> delete_list = new ArrayList<JSONObject>();

		// Process each record
		for (JonixRecord record : records) {
			if (record.product instanceof com.tectonica.jonix.onix3.Product) {
				com.tectonica.jonix.onix3.Product product = (com.tectonica.jonix.onix3.Product) record.product;
				JSONObject jsonline = processProduct(product, data_src);

				String notification_code = product.notificationType().value.code;
				if (NotificationOrUpdateTypes.Notification_confirmed_on_publication.getCode() == notification_code
						|| NotificationOrUpdateTypes.Advance_notification_confirmed.getCode() == notification_code
						|| NotificationOrUpdateTypes.Early_notification.getCode() == notification_code) {
					full_list.add(jsonline);
				} else if (NotificationOrUpdateTypes.Update_partial.getCode() == notification_code) {
					update_list.add(jsonline);
				} else if (NotificationOrUpdateTypes.Delete.getCode() == notification_code) {
					delete_list.add(jsonline);
				}
			} else { // Only process ONIX 3.
				throw new IllegalArgumentException();
			}
		}

		// Write the records out to different jsonlines files for full record, update
		// record, and deletion record messages.
		writeJsonl(full_list, output_dir, FULL_RECORD_FILE);
		writeJsonl(update_list, output_dir, UPDATE_RECORD_FILE);
		writeJsonl(delete_list, output_dir, DELETE_RECORD_FILE);
	}

	/**
	 * Write out Java json objects to jsonlines. See https://jsonlines.org/ for the
	 * spec.
	 * 
	 * @param json_list   List of json objects to write out.
	 * @param output_dir  Output directory to write the file to.
	 * @param output_file Name of the output file to use.
	 */
	private static void writeJsonl(List<JSONObject> json_list, String output_dir, String output_file) {
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(output_dir + "/" + output_file));

			for (JSONObject line : json_list) {
				String str = line.toString();
				out.write(str);
				out.write("\n");
			}
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Processes a Product record.
	 * 
	 * @param product Product record.
	 * @return Product record as a JSON object.
	 * @param data_src Data source name.
	 */
	private static JSONObject processProduct(com.tectonica.jonix.onix3.Product product, String data_src) {
		JSONObject jsonline = new JSONObject();

		if (product.recordSourceName().exists())
			jsonline.put("RecordSourceName", product.recordSourceName().value);

		if (product.recordSourceType().exists())
			jsonline.put("RecordSourceType", product.recordSourceType().value.description);

		processRecordReference(product.recordReference(), jsonline);
		processProductIdentifiers(product.productIdentifiers(), jsonline);
		processCollateralDetail(product.collateralDetail(), jsonline);
		// processContentDetail(product.contentDetail(), jsonline);
		processDescriptiveDetail(product.descriptiveDetail(), jsonline);
		processRelatedMaterial(product.relatedMaterial(), jsonline);
		processPublishingDetail(product.publishingDetail(), jsonline);

		// Add COKI internal identifier
		jsonline.put("COKI_ID", data_src + "_" + product.recordReference().value);

		return jsonline;
	}

	/**
	 * Process RelatedMaterial records.
	 * @param rel_mat     Related material object.
	 * @param jsonline    JSON object to write to.
	 */
	private static void processRelatedMaterial(RelatedMaterial rel_mat, JSONObject jsonline) {
		// processRelatedProducts()
		processRelatedWorks(rel_mat.relatedWorks(), jsonline);
	}

	/**
	 * Process RelatedWorks records.
	 * @param rel_works  List of RelatedWork record.
	 * @param jsonline   JSON object to write to.
	 */
	private static void processRelatedWorks(List<RelatedWork> rel_works,  JSONObject jsonline) {
		JSONArray jl_rel_works = new JSONArray();

		for(RelatedWork rel_work : rel_works) {
			JSONObject jl_rel_work = new JSONObject();

			if(rel_work.workRelationCode().value != null) {
				jl_rel_work.put("WorkRelationCode", rel_work.workRelationCode().value.description);
			}

			processWorkIdentifiers(rel_work.workIdentifiers(), jl_rel_work);

			jl_rel_works.put(jl_rel_work);
		}

		jsonline.put("RelatedWorks", jl_rel_works);
	}

	/**
	 * Process WorkIdentifier records.
	 * @param work_ids  List of WorkIdentifier records.
	 * @param jsonline  JSON object to write to.
	 */
	private static void processWorkIdentifiers(ListOfOnixDataCompositeWithKey<WorkIdentifier,JonixWorkIdentifier,WorkIdentifierTypes> work_ids, JSONObject jsonline) {
		JSONArray jl_work_ids = new JSONArray();

		for(WorkIdentifier work_id : work_ids) {
			JSONObject jl_work_id = new JSONObject();

			jl_work_id.put("IDTypeName", work_id.idTypeName().value);
			jl_work_id.put("IDValue", work_id.idValue().value);

			if(work_id.workIDType().value != null) {
				jl_work_id.put("WorkIDType", work_id.workIDType().value.description);
			}

			jl_work_ids.put(jl_work_id);
		}

		jsonline.put("WorkIdentifiers", jl_work_ids);
	}

	/**
	 * Process the PublishingDetail group of records.
	 * 
	 * @param pdet     Publishing detail object.
	 * @param jsonline JSON object to write to.
	 */
	private static void processPublishingDetail(PublishingDetail pdet, JSONObject jsonline) {
		if (!pdet.exists()) {
			return;
		}

		processCityofPublications(pdet.cityOfPublications(), jsonline);
		processImprints(pdet.imprints(), jsonline);
		// latestreprintnumber
		// productcontacts
		processPublishers(pdet.publishers(), jsonline);
		processPublishingDates(pdet.publishingDates(), jsonline);
		// publishingstatus
		// publishingstatusnotes
		// rowsalerightstype
		// salesrestrictions
		// salesrights
	}

	/**
	 * Process the Imprint records.
	 * 
	 * @param imprints List of Imprint objects.
	 * @param jsonline JSON object to write to.
	 */
	private static void processImprints(List<Imprint> imprints, JSONObject jsonline) {
		JSONArray jl_imprints = new JSONArray();

		for(Imprint imprint : imprints) {
			JSONObject jl_imprint = new JSONObject();
			processImprintName(imprint.imprintName(), jl_imprint);
			processImprintIdentifiers(imprint.imprintIdentifiers(), jl_imprint);
			jl_imprints.put(jl_imprint);
		}

		jsonline.put("Imprints", jl_imprints);
	}

	/**
	 * Process the ImprintName records.
	 * 
	 * @param iname    ImprintName object.
	 * @param jsonline JSON object to write to.
	 */
	private static void processImprintName(ImprintName iname, JSONObject jsonline) {
		jsonline.put("ImprintName", iname.value);

		if(iname.language != null) {
			jsonline.put("ImprintName_lang", iname.language.description);
		}
	}

	/**
	 * Process the ImprintIdentifier records.
	 * 
	 * @param iids     List of ImprintIdentifier objects.
	 * @param jsonline JSON object to write to.
	 */
	private static void processImprintIdentifiers(ListOfOnixDataCompositeWithKey<ImprintIdentifier,JonixImprintIdentifier,NameIdentifierTypes> iids, JSONObject jsonline) {
		JSONArray jl_iids = new JSONArray();

		for(ImprintIdentifier iid : iids) {
			if(!iid.exists()) {
				continue;
			}

			JSONObject jl_iid = new JSONObject();

			jl_iid.put("IDTypeName", iid.idTypeName().value);
			jl_iid.put("IDValue", iid.idValue().value);
			jl_iid.put("ImprintIDType", iid.imprintIDType().value);

			jl_iids.put(jl_iid);
		}

		jsonline.put("ImprintIdentifiers", jl_iids);
	}

	/**
	 * Process city of publications.
	 * @param cpubs List of city of publications
	 * @param jsonline JSON object to write to.
	 */
	private static void processCityofPublications(ListOfOnixElement<CityOfPublication,String> cpubs, JSONObject jsonline) {
		JSONArray jl_cpubs = new JSONArray();

		for(CityOfPublication cpub : cpubs) {
			jl_cpubs.put(cpub.value);
		}

		jsonline.put("CityOfPublications", jl_cpubs);
	}

	/**
	 * Process the PublishingDates.
	 * 
	 * @param pubdates List of publishing date objects.
	 * @param jsonline JSON object to write to.
	 */
	private static void processPublishingDates(
			ListOfOnixDataCompositeWithKey<PublishingDate, JonixPublishingDate, PublishingDateRoles> pubdates,
			JSONObject jsonline) {
		JSONArray jl_pubdates = new JSONArray();

		for (PublishingDate pubdate : pubdates) {
			JSONObject jl_pubdate = new JSONObject();

			jl_pubdate.put("Date", pubdate.date().value);

			if (pubdate.dateFormat().exists()) {
				jl_pubdate.put("DateFormat", pubdate.dateFormat().value.description);
			}

			if (pubdate.publishingDateRole().exists()) {
				jl_pubdate.put("PublishingDateRole", pubdate.publishingDateRole().value.description);
			}

			jl_pubdates.put(jl_pubdate);
		}

		jsonline.put("PublishingDates", jl_pubdates);
	}

	/**
	 * Process Publisher records.
	 * 
	 * @param publishers List of publishers.
	 * @param jsonline   JSON object to write to.
	 */
	private static void processPublishers(List<Publisher> publishers, JSONObject jsonline) {
		JSONArray jl_publishers = new JSONArray();

		for (Publisher publisher : publishers) {
			JSONObject jl_publisher = new JSONObject();

			jl_publisher.put("PublisherName", publisher.publisherName().value);

			if(publisher.publishingRole().value != null) {
				jl_publisher.put("PublishingRole", publisher.publishingRole().value.description);
			}

			processWebsites(publisher.websites(), jl_publisher);
			// identifiers
			// websites
			// fundings
			jl_publishers.put(jl_publisher);
		}

		jsonline.put("Publishers", jl_publishers);
	}

	/**
	 * Process CollateralDetail group of records.
	 * 
	 * @param cdet     Collateral detail object.
	 * @param jsonline JSON object to write to.
	 */
	private static void processCollateralDetail(CollateralDetail cdet, JSONObject jsonline) {
		if (!cdet.exists()) {
			return;
		}

		processTextContent(cdet.textContents(), jsonline);
		// citedcontents
		// prizes
		// supporting resources
	}

	/**
	 * Process TextContent records.
	 * 
	 * @param text_contents List of text contents.
	 * @param jsonline      JSON object to write to.
	 */
	private static void processTextContent(List<TextContent> text_contents, JSONObject jsonline) {
		JSONArray jl_text_contents = new JSONArray();

		for (TextContent text_content : text_contents) {
			JSONObject jl_text_content = new JSONObject();

			JSONArray texts = new JSONArray();

			for (Text text : text_content.texts()) {
				texts.put(text.value);
			}

			jl_text_content.put("Text", texts);

			if (text_content.textType().exists()) {
				jl_text_content.put("TextType", text_content.textType().value.description);
			}

			jl_text_contents.put(jl_text_content);
		}

		jsonline.put("TextContent", jl_text_contents);
	}

	/**
	 * Process RecordReference records.
	 * 
	 * @param record_ref List of RecordReferences.
	 * @param jsonline   JSON object to write to.
	 */
	private static void processRecordReference(com.tectonica.jonix.onix3.RecordReference record_ref,
			JSONObject jsonline) {
		if (!record_ref.exists()) {
			return;
		}

		jsonline.put("RecordRef", record_ref.value);
		jsonline.put("RecordRef_src", record_ref.sourcename);
		jsonline.put("RecordRef_ts", record_ref.datestamp);

		if (record_ref.sourcetype != null)
			jsonline.put("RecordRef_src_type", record_ref.sourcetype.description);
	}

	/**
	 * Process ProductIdentifier records.
	 * 
	 * @param pids     List of product identifiers.
	 * @param jsonline JSON object to write to.
	 */
	private static void processProductIdentifiers(
			ListOfOnixDataCompositeWithKey<ProductIdentifier, JonixProductIdentifier, ProductIdentifierTypes> pids,
			JSONObject jsonline) {
		jsonline.put("ISBN13", pids.find(ProductIdentifierTypes.ISBN_13).map(pid -> pid.idValue().value).orElse(null));
		jsonline.put("ISBN10", pids.find(ProductIdentifierTypes.ISBN_10).map(pid -> pid.idValue().value).orElse(null));
		jsonline.put("DOI", pids.find(ProductIdentifierTypes.DOI).map(pid -> pid.idValue().value).orElse(null));
		jsonline.put("ARK", pids.find(ProductIdentifierTypes.ARK).map(pid -> pid.idValue().value).orElse(null));
		jsonline.put("BNF_Control_number",
				pids.find(ProductIdentifierTypes.BNF_Control_number).map(pid -> pid.idValue().value).orElse(null));
		jsonline.put("Co_publisher_s_ISBN_13",
				pids.find(ProductIdentifierTypes.Co_publisher_s_ISBN_13).map(pid -> pid.idValue().value).orElse(null));
		jsonline.put("GTIN_13", pids.find(ProductIdentifierTypes.GTIN_13).map(pid -> pid.idValue().value).orElse(null));
		jsonline.put("GTIN_14", pids.find(ProductIdentifierTypes.GTIN_14).map(pid -> pid.idValue().value).orElse(null));
		jsonline.put("ISBN_A", pids.find(ProductIdentifierTypes.ISBN_A).map(pid -> pid.idValue().value).orElse(null));
		jsonline.put("ISMN_10", pids.find(ProductIdentifierTypes.ISMN_10).map(pid -> pid.idValue().value).orElse(null));
		jsonline.put("JP_e_code",
				pids.find(ProductIdentifierTypes.JP_e_code).map(pid -> pid.idValue().value).orElse(null));
		jsonline.put("JP_Magazine_ID",
				pids.find(ProductIdentifierTypes.JP_Magazine_ID).map(pid -> pid.idValue().value).orElse(null));
		jsonline.put("LCCN", pids.find(ProductIdentifierTypes.LCCN).map(pid -> pid.idValue().value).orElse(null));
		jsonline.put("Legal_deposit_number",
				pids.find(ProductIdentifierTypes.Legal_deposit_number).map(pid -> pid.idValue().value).orElse(null));
		jsonline.put("OCLC_number",
				pids.find(ProductIdentifierTypes.OCLC_number).map(pid -> pid.idValue().value).orElse(null));
		jsonline.put("OLCC_number",
				pids.find(ProductIdentifierTypes.OLCC_number).map(pid -> pid.idValue().value).orElse(null));
		jsonline.put("PID_Proprietary",
				pids.find(ProductIdentifierTypes.Proprietary).map(pid -> pid.idValue().value).orElse(null));
		jsonline.put("UPC", pids.find(ProductIdentifierTypes.UPC).map(pid -> pid.idValue().value).orElse(null));
		jsonline.put("UPC12_5", pids.find(ProductIdentifierTypes.UPC12_5).map(pid -> pid.idValue().value).orElse(null));
		jsonline.put("UPC12_5", pids.find(ProductIdentifierTypes.UPC12_5).map(pid -> pid.idValue().value).orElse(null));
		jsonline.put("URN", pids.find(ProductIdentifierTypes.URN).map(pid -> pid.idValue().value).orElse(null));
	}

	/**
	 * Process DescriptiveDetail group of records.
	 * 
	 * @param dd       DescriptiveDetail record.
	 * @param jsonline JSON object to write to.
	 */
	private static void processDescriptiveDetail(DescriptiveDetail dd, JSONObject jsonline) {
		jsonline.put("EditionNumber", dd.editionNumber().value);
		jsonline.put("EditionVersionNumber", dd.editionVersionNumber().value);

		if (!dd.isNoContributor())
			processContributors(dd.contributors(), jsonline);

		processSubjects(dd.subjects(), jsonline);

		if (dd.countryOfManufacture().exists()) {
			jsonline.put("CountryOfManufacture", dd.countryOfManufacture().value.code);
		}

		processTitleDetails(dd.titleDetails(), jsonline);
		processLanguages(dd.languages(), jsonline);
		processEditionTypes(dd.editionTypes(), jsonline);
		processExtents(dd.extents(), jsonline);
		
		if(!dd.isNoCollection()) {
			processCollections(dd.collections(), jsonline);
		}
	}

	/**
	 * Process Collection information.
	 * @param collections List of collection records.
	 * @param jsonline    JSON object to write to.
	 */
	private static void processCollections(List<com.tectonica.jonix.onix3.Collection> collections, JSONObject jsonline) {
		JSONArray jl_collections = new JSONArray();

		for(com.tectonica.jonix.onix3.Collection collection : collections) {
			JSONObject jl_collection = new JSONObject();

			if(collection.collectionType().value != null) {
				jl_collection.put("CollectionType", collection.collectionType().value.description);
			}

			processCollectionIdentifiers(collection.collectionIdentifiers(), jl_collection);
			processTitleDetails(collection.titleDetails(), jl_collection);

			jl_collections.put(jl_collection);
		}

		jsonline.put("Collections", jl_collections);
	}

	/**
	 * Process CollectionIdentifier information.
	 * @param col_id   List of CollectionIdentifier records.
	 * @param jsonline JSON object to write to.
	 */
	private static void processCollectionIdentifiers(ListOfOnixDataCompositeWithKey<CollectionIdentifier,JonixCollectionIdentifier,SeriesIdentifierTypes> col_ids, JSONObject jsonline) {
		JSONArray jl_col_ids = new JSONArray();

		for(CollectionIdentifier col_id : col_ids) {
			JSONObject jl_col_id = new JSONObject();

			jl_col_id.put("CollectionIdType", col_id.collectionIDType().value);
			jl_col_id.put("IDTypeName", col_id.idTypeName().value);
			jl_col_id.put("IDValue", col_id.idValue().value);

			jl_col_ids.put(jl_col_id);
		}

		jsonline.put("CollectionIdentifers", jl_col_ids);
	}

	/**
	 * Process EditionType records.
	 * 
	 * @param etypes   List of EditionType records.
	 * @param jsonline JSON object to write to.
	 */
	private static void processEditionTypes(ListOfOnixElement<EditionType, EditionTypes> etypes, JSONObject jsonline) {
		JSONArray jl_etypes = new JSONArray();

		for (EditionType etype : etypes) {
			if(etype.value != null) {
				jl_etypes.put(etype.value.description);
			}
		}

		jsonline.put("EditionType", jl_etypes);
	}

	/**
	 * Process Extent records.
	 * 
	 * @param etypes   List of Extent records.
	 * @param jsonline JSON object to write to.
	 */
	private static void processExtents(ListOfOnixDataCompositeWithKey<Extent, JonixExtent, ExtentTypes> extents,
			JSONObject jsonline) {
		JSONArray jl_extents = new JSONArray();

		for (Extent extent : extents) {
			JSONObject jl_extent = new JSONObject();

			if(extent.extentType().value != null) {
				jl_extent.put("ExtentType", extent.extentType().value.description);
			}

			if(extent.extentUnit().value != null) {
				jl_extent.put("ExtentUnit", extent.extentUnit().value.description);
			}

			jl_extent.put("ExtentValue", extent.extentValue().value);
			jl_extent.put("ExtentValueRoman", extent.extentValueRoman().value);

			jl_extents.put(jl_extent);
		}

		jsonline.put("Extent", jl_extents);
	}

	/**
	 * Process Language records.
	 * 
	 * @param languages List of Language records.
	 * @param jsonline  JSON object to write to.
	 */
	private static void processLanguages(
			ListOfOnixDataCompositeWithKey<Language, JonixLanguage, LanguageRoles> languages, JSONObject jsonline) {
		JSONArray jl_languages = new JSONArray();

		for (Language language : languages) {
			JSONObject jl_language = new JSONObject();

			if (!language.exists()) {
				continue;
			}

			if (language.countryCode().exists()) {
				jl_language.put("CountryCode", language.countryCode().value.code);
			}

			if (language.languageCode().exists()) {
				jl_language.put("LanguageCode", language.languageCode().value.code);
			}

			if (language.languageRole().exists()) {
				jl_language.put("LanguageRole", language.languageRole().value.description);
			}

			if (language.scriptCode().exists()) {
				jl_language.put("ScriptCode", language.scriptCode().value.description);
			}

			jl_languages.put(jl_language);
		}

		jsonline.put("Languages", jl_languages);
	}

	/**
	 * Process TitleDetail records.
	 * 
	 * @param details  List of TitleDetail records.
	 * @param jsonline JSON object to write to.
	 */
	private static void processTitleDetails(List<TitleDetail> details, JSONObject jsonline) {
		JSONArray jl_details = new JSONArray();

		for (TitleDetail detail : details) {
			JSONObject jl_detail = new JSONObject();

			if(detail.titleType().value != null) {
				jl_detail.put("TitleType", detail.titleType().value.description);
			}

			jl_detail.put("TitleStatement", detail.titleStatement().value);
			processTitleElements(detail.titleElements(), jl_detail);

			jl_details.put(jl_detail);
		}

		jsonline.put("TitleDetails", jl_details);
	}

	/**
	 * Process TitleElement records.
	 * 
	 * @param elements List of TitleElement records.
	 * @param jsonline JSON object to write to.
	 */
	private static void processTitleElements(ListOfOnixDataComposite<TitleElement, JonixTitleElement> elements,
			JSONObject jsonline) {
		JSONArray jl_elements = new JSONArray();

		for (TitleElement element : elements) {
			JSONObject jl_element = new JSONObject();

			jl_element.put("SequenceNumber", element.sequenceNumber().value);

			if(element.titleElementLevel().value != null) {
				jl_element.put("TitleElementLevel", element.titleElementLevel().value.description);
			}

			jl_element.put("YearOfAnnual", element.yearOfAnnual().value);

			processPartNumber(element.partNumber(), jl_element);
			processSubtitle(element.subtitle(), jl_element);
			processTitlePrefix(element.titlePrefix(), jl_element);
			processTitleWithoutPrefix(element.titleWithoutPrefix(), jl_element);
			processTitleText(element.titleText(), jl_element);

			jl_elements.put(jl_element);
		}

		jsonline.put("TitleElements", jl_elements);
	}

	/**
	 * Process TitleText records.
	 * 
	 * @param elements TitleElement record.
	 * @param jsonline JSON object to write to.
	 */
	private static void processTitleText(TitleText tt, JSONObject jsonline) {
		if (!tt.exists()) {
			return;
		}

		if (tt.language != null) {
			jsonline.put("TitleText_Language", tt.language.description);
		}

		if (tt.textscript != null) {
			jsonline.put("TitleText_TextScript", tt.textscript.description);
		}

		if (tt.textcase != null) {
			jsonline.put("TitleText_TextCaseFlags", tt.textcase.description);
		}

		jsonline.put("TitleText", tt.value);
	}

	/**
	 * Process PartNumber records.
	 * 
	 * @param pnum     PartNumber record.
	 * @param jsonline JSON object to write to.
	 */
	private static void processPartNumber(PartNumber pnum, JSONObject jsonline) {
		JSONObject jl_pnum = new JSONObject();

		if (!pnum.exists()) {
			return;
		}

		if(pnum.language != null) {
			jl_pnum.put("Language", pnum.language.description);
		}

		if(pnum.textscript != null) {
			jl_pnum.put("TextScript", pnum.textscript.description);
		}

		jl_pnum.put("Value", pnum.value);

		jsonline.put("PartNumber", jl_pnum);
	}

	/**
	 * Process TitleWithoutPrefix records.
	 * 
	 * @param tpref    TitleWithoutPrefix record.
	 * @param jsonline JSON object to write to.
	 */
	private static void processTitleWithoutPrefix(TitleWithoutPrefix tpref, JSONObject jsonline) {
		if (!tpref.exists()) {
			return;
		}

		if (tpref.language != null) {
			jsonline.put("TitleWithoutPrefix_LanguageCode", tpref.language.description);
		}

		if (tpref.textscript != null) {
			jsonline.put("TitleWithoutPrefix_TextScript", tpref.textscript.description);
		}

		if (tpref.textcase != null) {
			jsonline.put("TitleWithoutPrefix_TextCaseFlags", tpref.textcase.description);
		}

		jsonline.put("TitleWithoutPrefix", tpref.value);
	}

	/**
	 * Process TitlePrefix records.
	 * 
	 * @param tpref    TitlePrefix record.
	 * @param jsonline JSON object to write to.
	 */
	private static void processTitlePrefix(TitlePrefix tpref, JSONObject jsonline) {
		JSONObject jl_tpref = new JSONObject();

		if (!tpref.exists()) {
			return;
		}

		if (tpref.language != null) {
			jl_tpref.put("Language", tpref.language.description);
		}

		if (tpref.textscript != null) {
			jl_tpref.put("TextScript", tpref.textscript.description);
		}

		if (tpref.textcase != null) {
			jl_tpref.put("TextCaseFlags", tpref.textcase.description);
		}

		jl_tpref.put("Value", tpref.value);

		jsonline.put("TitlePrefix", jl_tpref);
	}

	/**
	 * Process Subtitle records.
	 * 
	 * @param subtitle Subtitle record.
	 * @param jsonline JSON object to write to.
	 */
	private static void processSubtitle(Subtitle subtitle, JSONObject jsonline) {
		// JSONObject jl_subtitle = new JSONObject();

		if (!subtitle.exists()) {
			return;
		}

		if (subtitle.language != null) {
			jsonline.put("Subtitle_Language", subtitle.language.description);
		}

		if (subtitle.textscript != null) {
			jsonline.put("Subtitle_TextScript", subtitle.textscript.description);
		}

		if (subtitle.textcase != null) {
			jsonline.put("Subtitle_TextCaseFlags", subtitle.textcase.description);
		}

		// jl_subtitle.put("Value", subtitle.value);

		jsonline.put("Subtitle", subtitle.value);
	}

	/**
	 * Process Subject records.
	 * 
	 * @param subjects List of Subject records.
	 * @param jsonline JSON object to write to.
	 */
	private static void processSubjects(ListOfOnixDataComposite<Subject, JonixSubject> subjects, JSONObject jsonline) {
		JSONArray jl_subjects = new JSONArray();

		for (Subject subject : subjects) {
			JSONObject jl_subject = new JSONObject();

			jl_subject.put("MainSubject", subject.isMainSubject());
			jl_subject.put("SubjectCode", subject.subjectCode().value);

			JSONArray heading_texts = new JSONArray();
			for (SubjectHeadingText text : subject.subjectHeadingTexts()) {
				heading_texts.put(text.value);
			}

			jl_subject.put("SubjectHeadingText", heading_texts);
			jl_subject.put("SubjectSchemeIdentifier", subject.subjectSchemeIdentifier().value);
			jl_subject.put("SubjectSchemeVersion", subject.subjectSchemeVersion().value);
			jl_subject.put("SubjectSchemeName", subject.subjectSchemeName().value);

			if (subject.subjectSchemeName().language != null) {
				jl_subject.put("SubjectSchemeNameLanguage", subject.subjectSchemeName().language.description);
			}

			jl_subjects.put(jl_subject);
		}

		jsonline.put("Subjects", jl_subjects);
	}

	/**
	 * Process Contributor records.
	 * 
	 * @param contributors List of Contributor records.
	 * @param jsonline     JSON object to write to.
	 */
	private static void processContributors(List<Contributor> contributors, JSONObject jsonline) {
		JSONArray jl_contributors = new JSONArray();

		for (Contributor contributor : contributors) {
			JSONObject jl_contributor = new JSONObject();
			jl_contributor.put("PersonName", contributor.personName().value);
			jl_contributor.put("PersonNameInverted", contributor.personNameInverted().value);
			jl_contributor.put("NamesAfterKey", contributor.namesAfterKey().value);
			jl_contributor.put("NamesBeforeKey", contributor.namesBeforeKey().value);

			if (contributor.nameType().exists()) {
				jl_contributor.put("NameType", contributor.nameType().value.description);
			}

			jl_contributor.put("LettersAfterNames", contributor.lettersAfterNames().value);
			jl_contributor.put("KeyNames", contributor.keyNames().value);
			jl_contributor.put("CorprorateName", contributor.corporateName().value);
			jl_contributor.put("CorprorateNameInverted", contributor.corporateNameInverted().value);

			if (contributor.unnamedPersons().value != null) {
				jl_contributor.put("UnnamedPersons", contributor.unnamedPersons().value.description);
			}

			if (contributor.gender().value != null) {
				jl_contributor.put("Gender", contributor.gender().value.description);
			}

			if (contributor.sequenceNumber().exists()) {
				jl_contributor.put("SequenceNumber", contributor.sequenceNumber().value);
			}

			jl_contributor.put("TitlesBeforeNames", contributor.titlesBeforeNames().value);
			jl_contributor.put("TitlesAfterNames", contributor.titlesAfterNames().value);
			jl_contributor.put("PrefixToKey", contributor.prefixToKey().value);
			jl_contributor.put("SuffixToKey", contributor.suffixToKey().value);

			processContributorDates(contributor.contributorDates(), jl_contributor);
			processContributorRoles(contributor.contributorRoles(), jl_contributor);
			processContributorPlaces(contributor.contributorPlaces(), jl_contributor);
			processNameIdentifiers(contributor.nameIdentifiers(), jl_contributor);
			processProfessionalAffiliations(contributor.professionalAffiliations(), jl_contributor);
			processAlternativeNames(contributor.alternativeNames(), jl_contributor);
			processWebsites(contributor.websites(), jl_contributor);
			processBiographicalNotes(contributor.biographicalNotes(), jl_contributor);

			jl_contributors.put(jl_contributor);
		}

		jsonline.put("Contributors", jl_contributors);
	}

	/**
	 * Process BiographicalNote records.
	 * 
	 * @param notes    List of BiographicalNote records.
	 * @param jsonline JSON object to write to.
	 */
	private static void processBiographicalNotes(ListOfOnixElement<BiographicalNote, String> notes,
			JSONObject jsonlines) {
		JSONArray jl_notes = new JSONArray();

		for (BiographicalNote note : notes) {
			JSONObject jl_note = new JSONObject();

			if (note.language != null) {
				jl_note.put("Language", note.language.description);
			}

			if (note.textformat != null) {
				jl_note.put("TextFormat", note.textformat.description);
			}

			jl_note.put("Note", note.value);

			jl_notes.put(jl_note);
		}

		jsonlines.put("BiographicalNotes", jl_notes);
	}

	/**
	 * Process Website records.
	 * 
	 * @param websites List of website records.
	 * @param jsonline JSON object to write to.
	 */
	private static void processWebsites(ListOfOnixDataComposite<Website, JonixWebsite> websites, JSONObject jsonlines) {
		JSONArray jl_websites = new JSONArray();

		for (Website website : websites) {
			JSONObject jl_website = new JSONObject();

			if (website.websiteRole().exists()) {
				jl_website.put("WebsiteRole", website.websiteRole().value.description);
			}

			processWebsiteDescriptions(website.websiteDescriptions(), jl_website);
			processWebsiteLinks(website.websiteLinks(), jl_website);

			jl_websites.put(jl_website);
		}

		jsonlines.put("Websites", jl_websites);
	}

	/**
	 * Process WebsiteLink records.
	 * 
	 * @param websites List of WebsiteLink records.
	 * @param jsonline JSON object to write to.
	 */
	private static void processWebsiteLinks(ListOfOnixElement<WebsiteLink, String> links, JSONObject jsonlines) {
		JSONArray jl_links = new JSONArray();

		for (WebsiteLink link : links) {
			jl_links.put(link.value);
		}

		jsonlines.put("WebsiteLinks", jl_links);
	}

	/**
	 * Process WebsiteDescription records.
	 * 
	 * @param websites List of WebsiteDescription records.
	 * @param jsonline JSON object to write to.
	 */
	private static void processWebsiteDescriptions(ListOfOnixElement<WebsiteDescription, String> descriptions,
			JSONObject jsonlines) {
		JSONArray jl_descriptions = new JSONArray();

		for (WebsiteDescription desc : descriptions) {
			jl_descriptions.put(desc.value);
		}

		jsonlines.put("WebsiteDescriptions", jl_descriptions);
	}

	/**
	 * Process AlternativeName records.
	 * 
	 * @param websites List of AlternativeName records.
	 * @param jsonline JSON object to write to.
	 */
	private static void processAlternativeNames(List<AlternativeName> alt_names, JSONObject jsonlines) {
		JSONArray jl_alt_names = new JSONArray();

		for (AlternativeName alt_name : alt_names) {
			JSONObject jl_alt_name = new JSONObject();

			jl_alt_name.put("CorprorateName", alt_name.corporateName().value);
			jl_alt_name.put("CorprorateNameInverted", alt_name.corporateNameInverted().value);

			if (alt_name.gender().value != null) {
				jl_alt_name.put("Gender", alt_name.gender().value.description);
			}

			jl_alt_name.put("KeyNames", alt_name.keyNames().value);
			jl_alt_name.put("LettersAfterNames", alt_name.lettersAfterNames().value);
			processNameIdentifiers(alt_name.nameIdentifiers(), jl_alt_name);

			jl_alt_name.put("NamesAfterKey", alt_name.namesAfterKey().value);
			jl_alt_name.put("NamesBeforeKey", alt_name.namesBeforeKey().value);

			if (alt_name.nameType().exists()) {
				jl_alt_name.put("NameType", alt_name.nameType().value.description);
			}

			jl_alt_name.put("PersonName", alt_name.personName().value);
			jl_alt_name.put("PersonNameInverted", alt_name.personNameInverted().value);
			jl_alt_name.put("PrefixToKey", alt_name.prefixToKey().value);
			jl_alt_name.put("SuffixToKey", alt_name.suffixToKey().value);
			jl_alt_name.put("TitlesBeforeNames", alt_name.titlesBeforeNames().value);
			jl_alt_name.put("TitlesAfterNames", alt_name.titlesAfterNames().value);

			jl_alt_names.put(jl_alt_name);
		}

		jsonlines.put("AlternativeNames", jl_alt_names);
	}

	/**
	 * Process ContributorDate records.
	 * 
	 * @param dates    List of ContributorDate records.
	 * @param jsonline JSON object to write to.
	 */
	private static void processContributorDates(
			ListOfOnixDataCompositeWithKey<ContributorDate, JonixContributorDate, PersonOrganizationDateRoles> dates,
			JSONObject jsonline) {
		JSONArray jl_dates = new JSONArray();

		for (ContributorDate date : dates) {
			JSONObject jl_date = new JSONObject();

			if(date.contributorDateRole().value != null) {
				jl_date.put("Role", date.contributorDateRole().value.description);
			}

			jl_date.put("Date", date.date().value);

			if(date.dateFormat().value != null) {
				jl_date.put("Format", date.dateFormat().value.description);
			}

			jl_dates.put(jl_date);
		}

		jsonline.put("Dates", jl_dates);
	}

	/**
	 * Process ContributorPlace records.
	 * 
	 * @param places   List of ContributorPlace records.
	 * @param jsonline JSON object to write to.
	 */
	private static void processContributorPlaces(
			ListOfOnixDataComposite<ContributorPlace, JonixContributorPlace> places, JSONObject jsonline) {
		JSONArray jl_contributor_places = new JSONArray();

		for (ContributorPlace place : places) {
			JSONObject jl_place = new JSONObject();
			JSONArray location_names = new JSONArray();

			if(place.contributorPlaceRelator().value != null) {
				jl_place.put("Relation", place.contributorPlaceRelator().value.description);
			}

			if(place.countryCode().value != null) {
				jl_place.put("CountryCode", place.countryCode().value.description);
			}

			if(place.regionCode().value != null) {
				jl_place.put("RegionCode", place.regionCode().value.description);
			}

			for (LocationName location : place.locationNames()) {
				location_names.put(location.value);
			}
			jl_place.put("Locations", location_names);

			jl_contributor_places.put(jl_place);
		}

		jsonline.put("Places", jl_contributor_places);
	}

	/**
	 * Process ContributorRole records.
	 * 
	 * @param roles    List of ContributorRole records.
	 * @param jsonline JSON object to write to.
	 */
	private static void processContributorRoles(ListOfOnixElement<ContributorRole, ContributorRoles> roles,
			JSONObject jsonline) {
		JSONArray str_roles = new JSONArray();

		for (ContributorRole role : roles) {
			if(role.value != null) {
				str_roles.put(role.value.description);
			}
		}

		jsonline.put("Roles", str_roles);
	}

	/**
	 * Process ProfessionalAffiliation records.
	 * 
	 * @param paffils  List of ProfessionalAffiliation records.
	 * @param jsonline JSON object to write to.
	 */
	private static void processProfessionalAffiliations(
			ListOfOnixDataComposite<ProfessionalAffiliation, JonixProfessionalAffiliation> paffils,
			JSONObject jsonline) {
		JSONArray jl_affils = new JSONArray();

		for (ProfessionalAffiliation affil : paffils) {
			JSONObject jl_affil = new JSONObject();

			jl_affil.put("Affiliations", affil.affiliation().value);

			JSONArray positions = new JSONArray();
			for (ProfessionalPosition position : affil.professionalPositions()) {
				positions.put(position.value);
			}

			jl_affil.put("Positions", positions);

			jl_affils.put(jl_affil);
		}

		jsonline.put("ProfessionalAffiliations", jl_affils);
	}

	/**
	 * Process NameIdentifier records.
	 * 
	 * @param nids     List of NameIdentifier records.
	 * @param jsonline JSON object to write to.
	 */
	private static void processNameIdentifiers(
			ListOfOnixDataCompositeWithKey<NameIdentifier, JonixNameIdentifier, NameIdentifierTypes> nids,
			JSONObject jsonline) {
		// JSONObject jl_name_identifiers = new JSONObject();
		JSONObject jl_name_identifiers = jsonline;

		jl_name_identifiers.put("ARK", nids.find(NameIdentifierTypes.ARK).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("B_rsenverein_Verkehrsnummer", nids
				.find(NameIdentifierTypes.B_rsenverein_Verkehrsnummer).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("BNE_CN",
				nids.find(NameIdentifierTypes.BNE_CN).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("BNF_Control_Number",
				nids.find(NameIdentifierTypes.BNF_Control_Number).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("Centraal_Boekhuis_Relatie_ID", nids
				.find(NameIdentifierTypes.Centraal_Boekhuis_Relatie_ID).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("DNB_publisher_identifier",
				nids.find(NameIdentifierTypes.DNB_publisher_identifier).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("DUNS",
				nids.find(NameIdentifierTypes.DUNS).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("EIDR_Party_DOI",
				nids.find(NameIdentifierTypes.EIDR_Party_DOI).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("Fondscode_Boekenbank",
				nids.find(NameIdentifierTypes.Fondscode_Boekenbank).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("FundRef_DOI",
				nids.find(NameIdentifierTypes.FundRef_DOI).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("GAPP_Publisher_Identifier",
				nids.find(NameIdentifierTypes.GAPP_Publisher_Identifier).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("German_ISBN_Agency_publisher_identifier",
				nids.find(NameIdentifierTypes.German_ISBN_Agency_publisher_identifier).map(nid -> nid.idValue().value)
						.orElse(null));
		jl_name_identifiers.put("GKD", nids.find(NameIdentifierTypes.GKD).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("GLN", nids.find(NameIdentifierTypes.GLN).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("GND", nids.find(NameIdentifierTypes.GND).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("GRID",
				nids.find(NameIdentifierTypes.GRID).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("Identifiant_Editeur_Electre", nids
				.find(NameIdentifierTypes.Identifiant_Editeur_Electre).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("Identifiant_Marque_Electre",
				nids.find(NameIdentifierTypes.Identifiant_Marque_Electre).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("ISNI",
				nids.find(NameIdentifierTypes.ISNI).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("Japanese_Publisher_identifier", nids
				.find(NameIdentifierTypes.Japanese_Publisher_identifier).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("JP_Distribution_Identifier",
				nids.find(NameIdentifierTypes.JP_Distribution_Identifier).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("LCCN",
				nids.find(NameIdentifierTypes.LCCN).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("MARC_organization_code",
				nids.find(NameIdentifierTypes.MARC_organization_code).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("Nasjonalt_autoritetsregister", nids
				.find(NameIdentifierTypes.Nasjonalt_autoritetsregister).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("ORCID",
				nids.find(NameIdentifierTypes.ORCID).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("PND", nids.find(NameIdentifierTypes.PND).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("Proprietary",
				nids.find(NameIdentifierTypes.Proprietary).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("Proprietary_",
				nids.find(NameIdentifierTypes.Proprietary_).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("Ringgold_ID",
				nids.find(NameIdentifierTypes.Ringgold_ID).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("SAN", nids.find(NameIdentifierTypes.SAN).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("VAT_Identity_Number",
				nids.find(NameIdentifierTypes.VAT_Identity_Number).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("VIAF_ID",
				nids.find(NameIdentifierTypes.VIAF_ID).map(nid -> nid.idValue().value).orElse(null));
		jl_name_identifiers.put("Y_tunnus",
				nids.find(NameIdentifierTypes.Y_tunnus).map(nid -> nid.idValue().value).orElse(null));

		// jsonline.put("NameIdentifiers", jl_name_identifiers);
	}

}
