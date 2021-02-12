/*
 * Modifications copyright (C) 2019 Christian Chevalley, Vitasystems GmbH and Hannover Medical School.

 * This file is part of Project EHRbase

 * Copyright (c) 2015 Christian Chevalley
 * This file is part of Project Ethercis
 *
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
 */

package org.ehrbase.aql.sql.queryimpl;

import org.apache.commons.lang3.StringUtils;
import org.ehrbase.serialisation.attributes.EntryAttributes;
import org.ehrbase.serialisation.attributes.LocatableAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.ehrbase.serialisation.dbencoding.CompositionSerializer.TAG_UID;

/**
 * Map a datavalue UML expression from an ARCHETYPED structure into its RM/JSON representation
 * see http://www.openehr.org/releases/trunk/UML/ for field identification
 * Created by christian on 5/11/2016.
 */
@SuppressWarnings({"java:S3776","java:S3740"})
public class EntryAttributeMapper {

    public static final String ISM_TRANSITION = "ism_transition";
    public static final String NAME = "name";
    public static final String TIME = "time";
    public static final String ORIGIN = "origin";
    public static final String OTHER_PARTICIPATIONS = "other_participations";
    public static final String SLASH_VALUE = "/value";
    public static final String VALUE = "value";
    public static final String DEFINING_CODE = "defining_code";
    public static final String SLASH = "/";
    public static final String COMMA = ",";
    public static final String LOWER = "lower";
    public static final String UPPER = "upper";
    public static final String INTERVAL = "interval";
    public static final String MAPPINGS = "mappings";
    public static final String FEEDER_AUDIT = "feeder_audit";
    public static final String CONTEXT = "context";


    private EntryAttributeMapper(){}

    private static Integer firstOccurence(int offset, List<String> list, String match) {
        for (int i = offset; i < list.size(); i++) {
            if (list.get(i).equals(match))
                return i;
        }
        return null;
    }

    /**
     * do a simple toCamelCase translation... and prefix all with /value :-)
     *
     * @param attribute
     * @return
     */
    public static String map(String attribute) {
        List<String> fields = new ArrayList<>();

        fields.addAll(Arrays.asList(attribute.split(SLASH)));
        fields.remove(0);

        int floor = 1;

        if (fields.isEmpty())
            return null; //this happens when a non specified value is queried f.e. the whole json body

        //deals with the tricky ones first...
        if (fields.get(0).equals(ISM_TRANSITION)) {
            //get the next key and concatenate...
            String subfield = fields.get(1);
            fields.remove(0);
            fields.set(0, ISM_TRANSITION + SLASH + subfield);
            if (!fields.get(1).equals(NAME)) {
                fields.add(1, SLASH_VALUE);
            }
            floor = 2;
        } else if (fields.get(0).equals(OTHER_PARTICIPATIONS)) { //insert a tag value
            fields.set(0, OTHER_PARTICIPATIONS);
            fields.add(1, "0");
            fields.add(2, SLASH_VALUE);
            floor = 3;
        } else if (fields.get(0).equals(NAME)) {
            fields.add(1, "0"); //name is now formatted as /name -> array of values! Required to deal with cluster items
        } else if (fields.size() >= 2 && fields.get(1).equals(MAPPINGS)) {
            fields.add(2, "0"); //mappings is now formatted as /mappings -> array of values!
        }else if (fields.get(0).equals(TIME) || fields.get(0).equals(ORIGIN)) {
            if (fields.size() > 1 && fields.get(1).equals(VALUE)) {
                fields.add(VALUE); //time is formatted with 2 values: string value and epoch_offset
                fields.set(1, SLASH_VALUE);
            }
        } else if (LocatableAttributes.isLocatableAttribute("/"+fields.get(0))){
            fields = setLocatableField(fields);
        } else if (EntryAttributes.isEntryAttribute("/"+fields.get(0))){
            fields = setEntryAttributeField(fields);
        }
        else { //this deals with the "/value,value"
            Integer match = firstOccurence(0, fields, VALUE);

            if (match != null) { //deals with "/value/value"
                Integer ndxInterval;

                if ((ndxInterval = intervalValueIndex(fields)) > 0) { //interval
                    fields.add(ndxInterval, INTERVAL);
                } else if (match != 0) {
                    //deals with name/value (name value is contained into a list conventionally)
                    if (match > 1 && fields.get(match - 1).matches("name|time"))
                        fields.set(match, VALUE);
                    else
                        //usual /value
                        fields.set(match, SLASH_VALUE);

                } else if (match + 1 < fields.size() - 1) {
                    Integer first = firstOccurence(match + 1, fields, VALUE);
                    if (first != null && first == match + 1)
                        fields.set(match + 1, SLASH_VALUE);
                }
            }
        }

        //deals with snake vs camel case
        boolean useCamelCase = true;

        if (FEEDER_AUDIT.equalsIgnoreCase(fields.get(0)))
            useCamelCase = false;

        //prefix the first element
        fields.set(0, SLASH + fields.get(0));

        //deals with the remainder of the array
        for (int i = floor; i < fields.size(); i++) {

            if (fields.get(i).equalsIgnoreCase("NAME")){
                //whenever the canonical json for name is queried
                fields.set(i, "/name,0");
            }
            else
                fields.set(i, useCamelCase ? NodeIds.toCamelCase(fields.get(i)) : fields.get(i));

        }

        return StringUtils.join(fields, COMMA);
    }

    private static Integer intervalValueIndex(List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).matches("^lower|^upper")) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> setLocatableField(List<String> fields){
        if (("/"+fields.get(0)).equals(TAG_UID)){
            fields.add(1, SLASH_VALUE);
        }
        return fields;
    }

    private static List<String> setEntryAttributeField(List<String> fields){
        return fields;
    }
}
