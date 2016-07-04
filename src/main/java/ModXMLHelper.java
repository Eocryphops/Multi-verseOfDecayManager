import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.dom4j.Document;
import org.dom4j.Element;

/**
 * Objective: <br>
 * Provide a class to orchestrate the acquisition, parsing, and application of ModXMLs <br>
 * <br>
 * Analysis: <br>
 * This class orchestrates the hard work to produce the results, 
 * from building up the XML, to requesting parsing, to triggering the mod changes. <br>
 * <br>
 * @author Phacops
 */
public class ModXMLHelper {

	boolean debug = true;
	ModFileUtil modFileUtil = new ModFileUtil();
	List<ModXML> modXMLs = new ArrayList<>();

	public List<ModXML> getModXMLs() {
		return modXMLs;
	}

	public void setModXMLs(List<ModXML> modXMLs) {
		this.modXMLs = modXMLs;
	}

	/**
	 * Iterate the established XML Mod Directory and aggregate the files into plain Java Objects for handling
	 * 
	 * @throws Exception
	 */
	public void findAllXmlModFilesAndBuildThem () throws Exception {
		modXMLs = modFileUtil.setupExpectedFiles();
	}
	
	/**
	 * The master method for orchestrating the entire operation
	 * 
	 * @throws Exception
	 */
	public void handleAllMods (boolean debug) throws Exception {
		this.debug = debug;
		
		System.out.println("[INFO] - ModXMLHelper found [" + modXMLs.size() + "] Mods!");
		
		handleModXMLFiles();
		modFileUtil.writeDomsToFiles();
		
		System.out.println("\n\n[INFO] - Finished handling all Mods - Huzzah!");
	}
	
	public void handleModXMLFiles () throws Exception {
		
		modFileUtil.checkAndBuildMissionAndFacilities();
		
		for (ModXML modXml : modXMLs) {
			List<ModChangeObjectContainer> modObjects = modXml.getModObjects();
			
			System.out.println("\n\n==========================="
					+ "\n[INFO] - Now loading: " + modXml.modName + ", "
						+ "which contained [" + modObjects.size() + "] overall change requests..."
					+ "\n===========================\n");
			
			for (ModChangeObjectContainer container : modObjects ) {
				handleChangesForMod(container);
				System.out.println("\n\n[INFO] - Completed handling for [file][mod_name][mod_section]: ["
						+ container.getFileToMod() + "][" + container.getModName() + "][" + container.getModSegment() + "]\n\n");
			}
		}
	}

	/**
	 * Specifically modify the original element that each mod requests
	 * 
	 * @param modChangeRequestContainer
	 */
	public void handleChangesForMod (ModChangeObjectContainer modChangeRequestContainer) {
		
		Element parentToMod = getExpectedParentElement(modChangeRequestContainer);
		parentToMod = removeModElementsIfPresent(modChangeRequestContainer.getModName(), parentToMod);
		parentToMod = addModElements(modChangeRequestContainer, parentToMod);
	}


	/**
	 * Accepts the mod element that holds the parent targeting, 
	 * which it will extract from the DOM passed<br>
	 * <br>
	 * Incoming mod element has attributes, e.g. for a Facilities mod:<br>
	 * parent_tag="Prefab"<br>
	 * parent_name="warehouse.workshop"<br>
	 * 
	 * @param domToAddModElementsTo
	 * @param modContainerElement
	 */
	public Element getExpectedParentElement(ModChangeObjectContainer modContainerElement) {

		Document facilitiesXmlFileDOM = modFileUtil.getFacilitiesXmlFileDOM();
		Document missionXmlFileDOM = modFileUtil.getMissionXmlFileDOM();
		Element domToAddTo = facilitiesXmlFileDOM.getRootElement();
		
		switch (modContainerElement.getFileToMod()) {
		case "CleanupOldModNames":
			@SuppressWarnings("unchecked")
			List<Element> eachOldName = modContainerElement.getChildElements().get(0).selectNodes("//Object");
			
			for (Element element : eachOldName) {
			removeModElementsIfPresent(
					element.valueOf("@name"), facilitiesXmlFileDOM.getRootElement(), true);
			removeModElementsIfPresent(
					element.valueOf("@name"), missionXmlFileDOM.getRootElement(), true);
			}
			//TODO - Rework structure so that Cleanup is pre-flight, not embedded
			return facilitiesXmlFileDOM.getRootElement();
		case "FacilitiesData":
			domToAddTo = facilitiesXmlFileDOM.getRootElement();
			break;
		case "MissionData":
			domToAddTo = missionXmlFileDOM.getRootElement();
			break;
		default:
			// TODO singular builder that gets all ModXMLs, confirms they are correctly constructed, and dumps them if they are not
			facilitiesXmlFileDOM.getRootElement();
		}
		

		String parentTag = modContainerElement.getParentTag();
		String parentName = modContainerElement.getParentName();


		@SuppressWarnings("unchecked")
		List<Element> domParentElement = (!StringUtils.isBlank(parentName)) 
				? domToAddTo.selectNodes("//" + parentTag + "[@Name='" + parentName + "']/Objects") : domToAddTo.selectNodes("//" + parentTag);

				if (domParentElement.size() > 1) {
					// In theory this shouldn't happen...but the element quantity is reasonably enormous so I'll assume my sample may be faulty
					System.out.println("[WARNING] - Quantity of potential parent elements was > 1 - "
							+ "Since only a person running from source would see this: "
							+ "please check your parent element targeting in the original mission or facilities file "
							+ "and assure that the name is a unique key");
				}

				if (debug) { System.out.println(domParentElement); };
				
				if (domParentElement == null || domParentElement.isEmpty()) {
					throw new NullPointerException("Could not find domParentElement for: \n" 
							+ modContainerElement + "\nIn DOM: \n" + domToAddTo);
				}
				// Relies on the assumption we only get one - merely allowing us to circumvent wielding an array
				return domParentElement.get(0);
	}

	/**
	 * Dump old versions of Elements, so long as you named them in the expected fashion with your mod_name
	 * One presumes that any version prior to this manager will require manual removal, of course
	 * 
	 * @param modName
	 * @param parentToRemoveFrom
	 * @return
	 */
	public void removeModElementsIfPresent (String modName, Element parentToRemoveFrom, boolean cleanup) {
		removeModElementsIfPresent(modName, parentToRemoveFrom);
	}
	
	/**
	 * Dump old versions of Elements, so long as you named them in the expected fashion with your mod_name
	 * One presumes that any version prior to this manager will require manual removal, of course
	 * 
	 * @param modName
	 * @param parentToRemoveFrom
	 * @return
	 */
	public Element removeModElementsIfPresent (String modName, Element parentToRemoveFrom) {

		@SuppressWarnings("unchecked")
		List<Element> potentialOldElements = parentToRemoveFrom.selectNodes("//*[@mod_name='" + modName + "']");

		if (potentialOldElements != null & !potentialOldElements.isEmpty()) {
			for (Element elementToRemove : potentialOldElements) {
				elementToRemove.detach();
			}
		}

		return parentToRemoveFrom;
	}

	/**
	 * The direct addition of the requested changes into the parent. <br>
	 * <br>
	 * Simply prepends all objects from the ModXML into the targetted parent element.<br>
	 * One additional operation is to prepend/append each side of the new elements
	 * with indicators of what mod they came from, and the author, 
	 * so it's much easier to see what's already in there.
	 * 
	 * @param modContainerElement
	 * @param parentToAddTo
	 * @return
	 */
	public Element addModElements (ModChangeObjectContainer modContainerElement, Element parentToAddTo) {

		System.out.println("[INFO] - Adding Elements to " + parentToAddTo.getPath() + " for: \n" + modContainerElement.toString());
		
		String modName = modContainerElement.getModName();
		List<Element> modChildObjectsToAdd = modContainerElement.getChildElements();
		
		if (debug) {
			System.out.println("[INFO] - modChildObjectsToAdd contained "
					+ "[" + modChildObjectsToAdd.size() + "]\n\n");
		}

		/* 
		 * Might look a little weird, but these do some wrapping to the mod data 
		 * to identify it visually when browsing the physical files; 
		 * that goes for the builder, wrapper doc, and the prepends surrounding the actual mod data.
		 */
		String modWrapperBuilder = "ModWrapper mod_name=\"" + modName 
				+ "\" mod_author=\"" + modContainerElement.getModAuthor() 
				+ "\" mod_segment=\"" + modContainerElement.getName() 
				+ "\" ";

		parentToAddTo.addElement(modWrapperBuilder + "placement=\"start_tag\" />");

		for (Element element : modChildObjectsToAdd) {
			element.addAttribute("mod_name", modName);

			parentToAddTo.addElement(element.asXML().replaceAll("^<(.*)/>$", "$1"));
		}

		parentToAddTo.addElement(modWrapperBuilder + "placement=\"end_tag\" ");

		return parentToAddTo;
	}
}
