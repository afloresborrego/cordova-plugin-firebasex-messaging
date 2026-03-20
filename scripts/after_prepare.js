#!/usr/bin/env node

/**
 * @file after_prepare.js
 * @brief Cordova "after_prepare" hook for the cordova-plugin-firebasex-messaging plugin.
 *
 * On Android, applies the notification accent colour (`ANDROID_ICON_ACCENT`) to the
 * platform's `colors.xml`, which controls the default tint applied to small notification
 * icons on Android Lollipop (5.0 / API 21) and above.
 *
 * Plugin variables are resolved using a 3-layer override strategy:
 * 1. Defaults from `plugin.xml` `<preference>` elements.
 * 2. Overrides from `config.xml` `<plugin><variable>` elements.
 * 3. Overrides from `package.json` `cordova.plugins` entries (highest priority).
 *
 * For layers 2 and 3, variables are checked under both the wrapper meta-plugin ID
 * (`cordova-plugin-firebasex`) and this plugin's own ID, allowing users who install
 * via the wrapper to specify variables under the original monolithic plugin name.
 *
 * @module scripts/after_prepare
 */
'use strict';

var fs = require('fs');
var path = require('path');
var parser = require('xml-js');

/** @constant {string} The plugin identifier. */
var PLUGIN_ID = "cordova-plugin-firebasex-messaging";
/** @constant {string} The wrapper meta-plugin identifier used as a fallback source for plugin variables. */
var WRAPPER_PLUGIN_ID = "cordova-plugin-firebasex";

/** @constant {string} Root directory of the Android platform. */
var ANDROID_PROJECT_ROOT = "platforms/android";
/** @constant {string} Path to the platform colors.xml where the accent colour is stored. */
var COLORS_XML_PATH = ANDROID_PROJECT_ROOT + "/app/src/main/res/values/colors.xml";
/** @constant {string} Path to the bundled colors.xml template inside the installed plugin. */
var COLORS_XML_TEMPLATE_PATH = path.join("plugins", PLUGIN_ID, "src", "android", "colors.xml");

/**
 * Resolves plugin variables using a 3-layer override strategy:
 * 1. Default values from `plugin.xml` `<preference>` elements.
 * 2. Overrides from `config.xml` `<plugin><variable>` elements.
 * 3. Overrides from `package.json` `cordova.plugins` entries (highest priority).
 *
 * @returns {Object} Resolved plugin variable key/value pairs.
 */
function getPluginVariables() {
    var variables = {};

    // Layer 1: Defaults from plugin.xml
    try {
        var pluginXmlPath = path.join("plugins", PLUGIN_ID, "plugin.xml");
        if (fs.existsSync(pluginXmlPath)) {
            var pluginXml = fs.readFileSync(pluginXmlPath, "utf-8");
            var prefRegex = /<preference\s+name="([^"]+)"\s+default="([^"]+)"\s*\/>/g;
            var match;
            while ((match = prefRegex.exec(pluginXml)) !== null) {
                variables[match[1]] = match[2];
            }
        }
    } catch (e) {
        console.warn("[FirebasexMessaging] Could not read plugin.xml: " + e.message);
    }

    // Layer 2: Overrides from config.xml (check both wrapper and own plugin ID)
    try {
        var configXmlPath = path.join("config.xml");
        if (fs.existsSync(configXmlPath)) {
            var configXml = fs.readFileSync(configXmlPath, "utf-8");
            [WRAPPER_PLUGIN_ID, PLUGIN_ID].forEach(function(pluginId) {
                var pluginRegex = new RegExp('<plugin[^>]+name="' + pluginId + '"[^>]*>(.*?)</plugin>', "s");
                var pluginMatch = configXml.match(pluginRegex);
                if (pluginMatch) {
                    var varRegex = /<variable\s+name="([^"]+)"\s+value="([^"]+)"\s*\/>/g;
                    var varMatch;
                    while ((varMatch = varRegex.exec(pluginMatch[1])) !== null) {
                        variables[varMatch[1]] = varMatch[2];
                    }
                }
            });
        }
    } catch (e) {
        console.warn("[FirebasexMessaging] Could not read config.xml: " + e.message);
    }

    // Layer 3: Overrides from package.json (highest priority)
    try {
        var packageJsonPath = path.join("package.json");
        if (fs.existsSync(packageJsonPath)) {
            var packageJson = JSON.parse(fs.readFileSync(packageJsonPath, "utf-8"));
            if (packageJson.cordova && packageJson.cordova.plugins) {
                [WRAPPER_PLUGIN_ID, PLUGIN_ID].forEach(function(pluginId) {
                    if (packageJson.cordova.plugins[pluginId]) {
                        var pluginVars = packageJson.cordova.plugins[pluginId];
                        for (var key in pluginVars) {
                            variables[key] = pluginVars[key];
                        }
                    }
                });
            }
        }
    } catch (e) {
        console.warn("[FirebasexMessaging] Could not read package.json: " + e.message);
    }

    return variables;
}

/**
 * Cordova hook entry point.
 *
 * Reads the `ANDROID_ICON_ACCENT` plugin variable and writes it as the `accent` colour
 * entry in the Android platform's `colors.xml` file.
 *
 * @param {object} context - The Cordova hook context.
 */
module.exports = function (context) {
    var platforms = context.opts.platforms;
    if (platforms.indexOf('android') === -1 || !fs.existsSync(path.resolve(ANDROID_PROJECT_ROOT))) {
        return;
    }

    var pluginVariables = getPluginVariables();
    var accentColor = pluginVariables['ANDROID_ICON_ACCENT'];

    if (!accentColor) {
        console.warn("[FirebasexMessaging] ANDROID_ICON_ACCENT not resolved; skipping accent colour update.");
        return;
    }

    var colorsXmlPath = path.resolve(COLORS_XML_PATH);
    if (!fs.existsSync(colorsXmlPath)) {
        // Bootstrap colors.xml from the plugin's bundled template.
        var templatePath = path.resolve(COLORS_XML_TEMPLATE_PATH);
        fs.mkdirSync(path.dirname(colorsXmlPath), { recursive: true });
        if (fs.existsSync(templatePath)) {
            fs.copyFileSync(templatePath, colorsXmlPath);
            console.log("[FirebasexMessaging] Created colors.xml from plugin template.");
        } else {
            fs.writeFileSync(colorsXmlPath, '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>\n');
            console.log("[FirebasexMessaging] Created minimal colors.xml.");
        }
    }

    try {
        var $colorsXml = JSON.parse(parser.xml2json(fs.readFileSync(colorsXmlPath, "utf-8"), {compact: true}));
        var $resources = $colorsXml.resources;
        var existingAccent = false;
        var writeChanges = false;

        if ($resources.color) {
            var $colors = $resources.color.length ? $resources.color : [$resources.color];
            $colors.forEach(function ($color) {
                if ($color._attributes.name === 'accent') {
                    existingAccent = true;
                    if ($color._text !== accentColor) {
                        $color._text = accentColor;
                        writeChanges = true;
                    }
                }
            });
        } else {
            $resources.color = {};
        }

        if (!existingAccent) {
            var $accentColor = {
                _attributes: { name: 'accent' },
                _text: accentColor
            };
            if ($resources.color && Object.keys($resources.color).length) {
                if (typeof $resources.color.length === 'undefined') {
                    $resources.color = [$resources.color];
                }
                $resources.color.push($accentColor);
            } else {
                $resources.color = $accentColor;
            }
            writeChanges = true;
        }

        if (writeChanges) {
            var xmlStr = parser.json2xml(JSON.stringify($colorsXml), {compact: true, spaces: 4});
            fs.writeFileSync(colorsXmlPath, xmlStr);
            console.log("[FirebasexMessaging] Updated colors.xml with accent colour: " + accentColor);
        } else {
            console.log("[FirebasexMessaging] colors.xml accent colour already set to: " + accentColor);
        }
    } catch (e) {
        console.warn("[FirebasexMessaging] Could not update colors.xml: " + e.message);
    }
};
