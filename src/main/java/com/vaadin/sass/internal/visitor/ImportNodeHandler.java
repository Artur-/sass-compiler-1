/*
 * Copyright 2000-2014 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.sass.internal.visitor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.css.sac.CSSException;

import com.vaadin.sass.internal.ScssStylesheet;
import com.vaadin.sass.internal.parser.ParseException;
import com.vaadin.sass.internal.parser.SassListItem;
import com.vaadin.sass.internal.tree.ImportNode;
import com.vaadin.sass.internal.tree.Node;
import com.vaadin.sass.internal.tree.RuleNode;

public class ImportNodeHandler {

    public static void traverse(Node node) {
        ScssStylesheet styleSheet = null;
        if (node instanceof ScssStylesheet) {
            styleSheet = (ScssStylesheet) node;
        } else {
            // iterate to parents of node, find ScssStylesheet
            Node parent = node.getParentNode();
            while (parent != null && !(parent instanceof ScssStylesheet)) {
                parent = parent.getParentNode();
            }
            if (parent instanceof ScssStylesheet) {
                styleSheet = (ScssStylesheet) parent;
            }
        }
        if (styleSheet == null) {
            throw new ParseException("Nested import in an invalid context");
        }
        ArrayList<Node> c = new ArrayList<Node>(node.getChildren());
        for (Node n : c) {
            if (n instanceof ImportNode) {
                ImportNode importNode = (ImportNode) n;
                if (!importNode.isPureCssImport()) {
                    try {
                        // set parent's charset to imported node.
                        ScssStylesheet imported = ScssStylesheet.get(
                                importNode.getUri(), styleSheet);
                        if (imported == null) {
                            throw new FileNotFoundException("Import '"
                                    + importNode.getUri() + "' in '"
                                    + styleSheet.getFileName()
                                    + "' could not be found");
                        }

                        traverse(imported);

                        String prefix = getUrlPrefix(importNode.getUri());
                        if (prefix != null) {
                            updateUrlInImportedSheet(imported, prefix);
                        }

                        node.replaceNode(importNode, new ArrayList<Node>(
                                imported.getChildren()));
                    } catch (CSSException e) {
                        Logger.getLogger(ImportNodeHandler.class.getName())
                                .log(Level.SEVERE, null, e);
                    } catch (IOException e) {
                        Logger.getLogger(ImportNodeHandler.class.getName())
                                .log(Level.SEVERE, null, e);
                    }
                } else {
                    if (styleSheet != node) {
                        throw new ParseException(
                                "CSS imports can only be used at the top level, not as nested imports. Within style rules, use SCSS imports.");
                    }
                }
            } else {
                for (Node child : new ArrayList<Node>(node.getChildren())) {
                    traverse(child);
                }
            }
        }
    }

    private static String getUrlPrefix(String url) {
        if (url == null) {
            return null;
        }
        int pos = url.lastIndexOf('/');
        if (pos == -1) {
            return null;
        }
        return url.substring(0, pos + 1);
    }

    private static void updateUrlInImportedSheet(Node node, String prefix) {
        for (Node child : node.getChildren()) {
            if (child instanceof RuleNode) {
                SassListItem value = ((RuleNode) child).getValue();
                if (value != null) {
                    value.updateUrl(prefix);
                }
            }
            updateUrlInImportedSheet(child, prefix);
        }
    }
}
