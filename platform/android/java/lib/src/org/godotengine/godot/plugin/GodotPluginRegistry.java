/**************************************************************************/
/*  GodotPluginRegistry.java                                              */
/**************************************************************************/
/*                         This file is part of:                          */
/*                             GODOT ENGINE                               */
/*                        https://godotengine.org                         */
/**************************************************************************/
/* Copyright (c) 2014-present Godot Engine contributors (see AUTHORS.md). */
/* Copyright (c) 2007-2014 Juan Linietsky, Ariel Manzur.                  */
/*                                                                        */
/* Permission is hereby granted, free of charge, to any person obtaining  */
/* a copy of this software and associated documentation files (the        */
/* "Software"), to deal in the Software without restriction, including    */
/* without limitation the rights to use, copy, modify, merge, publish,    */
/* distribute, sublicense, and/or sell copies of the Software, and to     */
/* permit persons to whom the Software is furnished to do so, subject to  */
/* the following conditions:                                              */
/*                                                                        */
/* The above copyright notice and this permission notice shall be         */
/* included in all copies or substantial portions of the Software.        */
/*                                                                        */
/* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,        */
/* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF     */
/* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. */
/* IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY   */
/* CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,   */
/* TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE      */
/* SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.                 */
/**************************************************************************/

package org.godotengine.godot.plugin;

import org.godotengine.godot.Godot;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry used to load and access the registered Godot Android plugins.
 */
public final class GodotPluginRegistry {
	private static final String TAG = GodotPluginRegistry.class.getSimpleName();

	/**
	 * Prefix used for version 1 of the Godot plugin, compatible with Godot 3.x
	 */
	private static final String GODOT_PLUGIN_V1_NAME_PREFIX = "org.godotengine.plugin.v1.";
	/**
	 * Prefix used for version 2 of the Godot plugin, compatible with Godot 4.x
	 */
	private static final String GODOT_PLUGIN_V2_NAME_PREFIX = "org.godotengine.plugin.v2.";

	private static GodotPluginRegistry instance;
	private final ConcurrentHashMap<String, GodotPlugin> registry;

	private GodotPluginRegistry(Godot godot) {
		registry = new ConcurrentHashMap<>();
		loadPlugins(godot);
	}

	/**
	 * Retrieve the plugin tied to the given plugin name.
	 * @param pluginName Name of the plugin
	 * @return {@link GodotPlugin} handle if it exists, null otherwise.
	 */
	@Nullable
	public GodotPlugin getPlugin(String pluginName) {
		return registry.get(pluginName);
	}

	/**
	 * Retrieve the full set of loaded plugins.
	 */
	public Collection<GodotPlugin> getAllPlugins() {
		return registry.values();
	}

	/**
	 * Parse the manifest file and load all included Godot Android plugins.
	 * <p>
	 * A plugin manifest entry is a '<meta-data>' tag setup as described in the {@link GodotPlugin}
	 * documentation.
	 *
	 * @param godot Godot instance
	 * @return A singleton instance of {@link GodotPluginRegistry}. This ensures that only one instance
	 * of each Godot Android plugins is available at runtime.
	 */
	public static GodotPluginRegistry initializePluginRegistry(Godot godot) {
		if (instance == null) {
			instance = new GodotPluginRegistry(godot);
		}

		return instance;
	}

	/**
	 * Return the plugin registry if it's initialized.
	 * Throws a {@link IllegalStateException} exception if not.
	 *
	 * @throws IllegalStateException if {@link GodotPluginRegistry#initializePluginRegistry(Godot)} has not been called prior to calling this method.
	 */
	public static GodotPluginRegistry getPluginRegistry() throws IllegalStateException {
		if (instance == null) {
			throw new IllegalStateException("Plugin registry hasn't been initialized.");
		}

		return instance;
	}

	private void loadPlugins(Godot godot) {
		try {
			final Activity activity = godot.getActivity();
			ApplicationInfo appInfo = activity
											  .getPackageManager()
											  .getApplicationInfo(activity.getPackageName(),
													  PackageManager.GET_META_DATA);
			Bundle metaData = appInfo.metaData;
			if (metaData == null || metaData.isEmpty()) {
				return;
			}

			int godotPluginV2NamePrefixLength = GODOT_PLUGIN_V2_NAME_PREFIX.length();
			for (String metaDataName : metaData.keySet()) {
				// Parse the meta-data looking for entry with the Godot plugin name prefix.
				if (metaDataName.startsWith(GODOT_PLUGIN_V2_NAME_PREFIX)) {
					String pluginName = metaDataName.substring(godotPluginV2NamePrefixLength).trim();
					Log.i(TAG, "Initializing Godot v2 plugin " + pluginName);

					// Retrieve the plugin class full name.
					String pluginHandleClassFullName = metaData.getString(metaDataName);
					if (!TextUtils.isEmpty(pluginHandleClassFullName)) {
						try {
							// Attempt to create the plugin init class via reflection.
							@SuppressWarnings("unchecked")
							Class<GodotPlugin> pluginClass = (Class<GodotPlugin>)Class
																	 .forName(pluginHandleClassFullName);
							Constructor<GodotPlugin> pluginConstructor = pluginClass
																				 .getConstructor(Godot.class);
							GodotPlugin pluginHandle = pluginConstructor.newInstance(godot);

							// Load the plugin initializer into the registry using the plugin name as key.
							if (!pluginName.equals(pluginHandle.getPluginName())) {
								Log.w(TAG,
										"Meta-data plugin name does not match the value returned by the plugin handle: " + pluginName + " =/= " + pluginHandle.getPluginName());
							}
							registry.put(pluginName, pluginHandle);
							Log.i(TAG, "Completed initialization for Godot v2 plugin " + pluginHandle.getPluginName());
						} catch (ClassNotFoundException | IllegalAccessException |
								InstantiationException | NoSuchMethodException |
								InvocationTargetException e) {
							Log.w(TAG, "Unable to load Godot v2 plugin " + pluginName, e);
						}
					} else {
						Log.w(TAG, "Invalid plugin loader class for " + pluginName);
					}
				} else if (metaDataName.startsWith(GODOT_PLUGIN_V1_NAME_PREFIX)) {
					String v1PluginName = metaDataName.substring(GODOT_PLUGIN_V1_NAME_PREFIX.length()).trim();
					Log.w(TAG, "Godot 4 does not support Godot 3 (v1) plugin: " + v1PluginName);
				}
			}
		} catch (PackageManager.NameNotFoundException e) {
			Log.e(TAG, "Unable load Godot Android v2 plugins from the manifest file.", e);
		}
	}
}
