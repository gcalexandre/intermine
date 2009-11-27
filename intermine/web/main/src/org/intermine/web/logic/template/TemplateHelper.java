package org.intermine.web.logic.template;

/*
 * Copyright (C) 2002-2009 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.log4j.Logger;
import org.intermine.api.profile.InterMineBag;
import org.intermine.api.template.TemplateQuery;
import org.intermine.api.template.TemplateValue;
import org.intermine.api.xml.TemplateQueryBinding;
import org.intermine.model.InterMineObject;
import org.intermine.objectstore.query.ConstraintOp;
import org.intermine.pathquery.Constraint;
import org.intermine.pathquery.PathNode;
import org.intermine.pathquery.PathQuery;
import org.intermine.web.struts.TemplateForm;

/**
 * Static helper routines related to templates.
 *
 * @author Thomas Riley
 * @author Richard Smith
 */
public class TemplateHelper
{
    private static final Logger LOG = Logger.getLogger(TemplateHelper.class);


    public static Map<String, List<TemplateValue>> templateFormToTemplateValues(TemplateForm tf,
                                                            TemplateQuery template) {
    	Map<String, List<TemplateValue>> templateValues = 
    		new HashMap<String, List<TemplateValue>>();
        int j = 0;
        for (PathNode node : template.getEditableNodes()) {
        	List<TemplateValue> nodeValues = new ArrayList<TemplateValue>();
        	templateValues.put(node.getPathString(), nodeValues);
        	for (Constraint c : template.getEditableConstraints(node)) {
                String key = "" + (j + 1);

                TemplateValue value;
                if (tf.getUseBagConstraint(key)) {
                	ConstraintOp constraintOp = ConstraintOp.getOpForIndex(Integer.valueOf(tf.getBagOp(key)));
                	Object constraintValue = tf.getBag(key);                	
                	value = new TemplateValue(node.getPathString(), constraintOp, constraintValue, c.getCode());
                } else {
                	 String op = (String) tf.getAttributeOps(key);
                     ConstraintOp constraintOp = ConstraintOp.getOpForIndex(Integer.valueOf(op));
                     Object constraintValue = tf.getAttributeValues(key);
                     Object extraValue = tf.getExtraValues(key);
                     value = new TemplateValue(node.getPathString(), constraintOp, constraintValue, c.getCode(), extraValue);
                }                    
                nodeValues.add(value);
        	}
        }	
    	return templateValues;
    }
    
    // TODO this method should be in api project
    // TODO use a better exception type
    public static  Map<String, List<TemplateValue>> objectToTemplateValues(TemplateQuery template,
            InterMineObject obj) {
        Map<String, List<TemplateValue>> templateValues = 
            new HashMap<String, List<TemplateValue>>();
        
        // TODO move out to common method between object and bag
        if (template.getAllEditableConstraints().size() != 1) {
            throw new RuntimeException("Template must have exactly one editable constraint to be "
                    + " configured with an object.");
        }
        
        // TODO check the types are compatible
        PathNode node = template.getEditableNodes().get(0);
            //String editableNodeType = 
            //if (!TypeUtil.isInstanceOf(obj, className))
            
        Constraint constraint = template.getEditableConstraints(node).get(0);
        TemplateValue templateValue = new TemplateValue(node.getPathString(), ConstraintOp.EQUALS, 
                obj, constraint.getCode());
        templateValue.setObjectConstraint(Boolean.TRUE);
        templateValues.put(node.getPathString(), 
                new ArrayList<TemplateValue>(Collections.singleton(templateValue)));
        
        return templateValues;
    }
    
    // TODO this method should be in api project
    // TODO use a better exception type
    public static  Map<String, List<TemplateValue>> bagToTemplateValues(TemplateQuery template,
            String bagName) {
        Map<String, List<TemplateValue>> templateValues = 
            new HashMap<String, List<TemplateValue>>();
        
        
        // TODO move out to common method between object and bag
        if (template.getAllEditableConstraints().size() != 1) {
            throw new RuntimeException("Template must have exactly one editable constraint to be "
                    + " configured with an object.");
        }
        
        // TODO check the types are compatible
        PathNode node = template.getEditableNodes().get(0);
            //String editableNodeType = 
            //if (!TypeUtil.isInstanceOf(obj, className))
            
        Constraint constraint = template.getEditableConstraints(node).get(0);
        TemplateValue templateValue = new TemplateValue(node.getPathString(), ConstraintOp.IN, 
                bagName, constraint.getCode());
        templateValue.setBagConstraint(Boolean.TRUE);
        templateValues.put(node.getPathString(), 
                new ArrayList<TemplateValue>(Collections.singleton(templateValue)));
        
        return templateValues;
    }

    public static Map<String, List<TemplateValue>> singleConstraintTemplateValues(
            TemplateQuery template, ConstraintOp op, Object value) {
        Map<String, List<TemplateValue>> templateValues = 
            new HashMap<String, List<TemplateValue>>();
        
        if (template.getAllEditableConstraints().size() != 1) {
            throw new RuntimeException("Template must have exactly one editable constraint to be "
                    + " configured with a single value.");
        }
        PathNode node = template.getEditableNodes().get(0);
        Constraint constraint = template.getEditableConstraints(node).get(0);
        TemplateValue templateValue = new TemplateValue(node.getPathString(), op, value,
                constraint.getCode());
        templateValues.put(node.getPathString(), 
                new ArrayList<TemplateValue>(Collections.singleton(templateValue)));
        
        return templateValues;        
    }
        
    
    /**
     * Given a Map of TemplateQuerys (mapping from template name to TemplateQuery)
     * return a string containing each template seriaised as XML. The root element
     * will be a <code>template-queries</code> element.
     *
     * @param templates  map from template name to TemplateQuery
     * @param version the version number of the XML format
     * @return  all template queries serialised as XML
     * @see  TemplateQuery
     */
    public static String templateMapToXml(Map templates, int version) {
        StringWriter sw = new StringWriter();
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        Iterator iter = templates.values().iterator();

        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(sw);
            writer.writeStartElement("template-queries");
            while (iter.hasNext()) {
                TemplateQueryBinding.marshal((TemplateQuery) iter.next(), writer, version);
            }
            writer.writeEndElement();
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }

        return sw.toString();
    }

    /**
     * Parse templates in XML format and return a map from template name to
     * TemplateQuery.
     *
     * @param xml         the template queries in xml format
     * @param savedBags   Map from bag name to bag
     * @param version the version of the xml format, an attribute on ProfileManager
     * @return            Map from template name to TemplateQuery
     * @throws Exception  when a parse exception occurs (wrapped in a RuntimeException)
     */
    public static Map xmlToTemplateMap(String xml, Map<String, InterMineBag> savedBags,
            int version) throws Exception {
        Reader templateQueriesReader = new StringReader(xml);
        return new TemplateQueryBinding().unmarshal(templateQueriesReader, savedBags, version);
    }

    /**
     * Build a template query given a TemplateBuildState and a PathQuery
     *
     * @param tbs the template build state
     * @param query the path query
     * @return a template query
     */
    public static TemplateQuery buildTemplateQuery(TemplateBuildState tbs, PathQuery query) {
        TemplateQuery template = new TemplateQuery(tbs.getName(), tbs.getTitle(),
                tbs.getDescription(), tbs.getComment(), query.clone());
        return template;
    }
}
