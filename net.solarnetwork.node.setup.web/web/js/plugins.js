SolarNode.Plugins = {
		
};

SolarNode.Plugins.runtime = {};

SolarNode.Plugins.refreshPluginList = function(url, container) {
	SolarNode.showLoading($('#plugins-refresh'));
	$.getJSON(url, function(data) {
		if ( data === undefined || data.success !== true || data.data === undefined ) {
			SolarNode.hideLoading($('#plugins-refresh'));
			// TODO: l10n
			SolarNode.warn('Error!', 'An error occured refreshing plugin information.', list);
			return;
		}
		SolarNode.Plugins.populateUI(container);
	});
};

SolarNode.Plugins.populateUI = function(container) {
	var url = SolarNode.context.path('/plugins/list');
	
	var groupNameForPlugin = function(plugin) {
		var match = plugin.uid.match(/^net\.solarnetwork\.node\.(\w+)/);
		if ( match == null ) {
			return plugin.uid;
		}
		var n = match[1];
		
		// some special cases here...
		// TODO: l10n
		if ( n === 'io' ) {
			return "Communication Support";
		}
		if ( n === 'hw' ) {
			return "Hardware Support";
		}
		
		// default: capitalize
		
		return (n.charAt(0).toUpperCase() + n.substring(1));
	};
	
	var groupPlugins = function(data) {
		var i, len;
		var plugin;
		var groupName;
		var result = {
			groupNames: [],	// String[]
			groups: {},   	// map of GroupName -> Plugin[]
			installed: {} 	// map of UID -> Plugin
		};
		for ( i = 0, len = data.availablePlugins.length; i < len; i++ ) {
			plugin = data.availablePlugins[i];
			groupName = groupNameForPlugin(plugin);
			if ( result.groups[groupName] === undefined ) {
				result.groupNames.push(groupName);
				// note we assume plugins already sorted by name
				result.groups[groupName] = [];
			}
			result.groups[groupName].push(plugin);
		}
		result.groupNames.sort();
		for ( i = 0, len = data.installedPlugins.length; i < len; i++ ) {
			plugin = data.installedPlugins[i];
			result.installed[plugin.uid] = plugin;
		}
		return result;
	};
	
	var createGroup = function(groupName, container) {
		var group = $('<div class="accordion-group"/>');
		var heading = $('<div class="accordion-heading"/>');
		var id = "Group-" +groupName.replace(/\W/g, '');
		$('<a class="accordion-toggle" data-toggle="collapse" data-parent="#plugin-list"/>').attr('href', '#'+id).text(groupName).appendTo(heading);
		heading.appendTo(group);
		var body = $('<div class="accordion-body collapse"/>').attr('id', id);
		if ( container.children().length === 0 ) {
			body.addClass('in');
		}
		body.appendTo(group);
		var innerBody = $('<div class="accordion-inner"/>');
		innerBody.appendTo(body);
		container.append(group);
		return innerBody;
	};
	
	var compareVersions = function(v1, v2) {
		var result = v1.major - v2.major;
		if ( result !== 0 ) {
			return result;
		}
		result = v1.minor - v2.minor;
		if ( result !== 0 ) {
			return result;
		}
		result = v1.micro - v2.micro;
		if ( result !== 0 ) {
			return result;
		}
		// ignoring qualifiers
		return 0;
	};
	
	var createPluginUI = function(plugin, installed) {
		var id = "Plugin-" +plugin.uid.replace(/\W/g, '-');
		var row = $('<div class="plugin clearfix"/>').attr('id', id).text(plugin.info.name);
		var actionContainer = $('<div class="pull-right"/>').appendTo(row);
		var installedPlugin = installed[plugin.uid];
		var button = undefined;
		if ( installedPlugin === undefined ) {
			// not installed; offer to install it
			button = $('<button class="btn btn-small btn-primary span2">').text('Install'); // TODO: l10n
			actionContainer.append(button);
		} else if ( compareVersions(plugin.version, installedPlugin.version) > 0 ) {
			// update available
			button = $('<button class="btn btn-small btn-info span2">').text('Upgrade'); // TODO: l10n
			actionContainer.append(button);
		} else {
			// installed
			button = $('<button class="btn btn-small btn-danger span2"/>').text('Remove');  // TODO: l10n
			actionContainer.append(button);
		}
		button.click(function() {
			SolarNode.Plugins.previewInstall(plugin);
		});
		return row;
	};
	
	SolarNode.showLoading($('#plugins-refresh'));
	$.getJSON(url, function(data) {
		SolarNode.hideLoading($('#plugins-refresh'));
		container.empty();
		if ( data === undefined || data.success !== true || data.data === undefined ) {
			// TODO: l10n
			SolarNode.warn('Error!', 'An error occured loading plugin information.', container);
			return;
		}
		var i, iMax;
		var j, jMax;
		var groupedPlugins = groupPlugins(data.data);
		var html = $('<div class="accordion" id="plugin-list"/>');
		var groupBody = undefined;
		var groupName = undefined;
		var group = undefined;
		var plugin = undefined;
		for ( i = 0, iMax = groupedPlugins.groupNames.length; i < iMax; i++ ) {
			groupName = groupedPlugins.groupNames[i];
			groupBody = createGroup(groupName, html);
			group = groupedPlugins.groups[groupName];
			for ( j = 0, jMax = group.length; j < jMax; j++ ) {
				plugin = group[j];
				groupBody.append(createPluginUI(plugin, groupedPlugins.installed));
			}
		}
		container.html(html);
	});
};

SolarNode.Plugins.previewInstall = function(plugin) {
	var form = $('#plugin-preview-install-modal');
	var previewURL = form.attr('action') + '?uid=' +encodeURIComponent(plugin.uid);
	var container = $('#plugin-preview-install-list').empty();
	var title = form.find('h3');
	title.text(title.data('msg-install') +' ' +plugin.info.name);
	form.find('input[name=uid]').val(plugin.uid);
	form.modal('show');
	$.getJSON(previewURL, function(data) {
		if ( data === undefined || data.success !== true || data.data === undefined ) {
			// TODO: l10n
			SolarNode.warn('Error!', 'An error occured loading plugin information.', list);
			return;
		}
		var i, len;
		var pluginToInstall;
		var list = $('<ul/>');
		var version;
		for ( i = 0, len = data.data.pluginsToInstall.length; i < len; i++ ) {
			pluginToInstall = data.data.pluginsToInstall[i];
			version = pluginToInstall.version.major + '.' + pluginToInstall.version.minor;
			if ( pluginToInstall.version.micro > 0 ) {
				version += '.' + pluginToInstall.version.micro;
			}
			$('<li/>').html('<b>' +pluginToInstall.info.name  
					+'</b> <span class="label">' +version +'</span>').appendTo(list);
		}
		container.append(list);
	});
};

SolarNode.Plugins.handleInstall = function(form) {
	var progressBar = form.find('.progress');
	var progressFill = progressBar.find('.bar');
	var installBtn = form.find('button[type=submit]');
	var errorContainer = $('#plugin-install-error');
	var refreshPluginListOnModalClose = false;
	var keepPollingForStatus = true;
	
	var showAlert = function(msg) {
		SolarNode.hideLoading(installBtn);
		progressBar.addClass('hide');
		SolarNode.error(SolarNode.i18n(installBtn.data('msg-error'), [msg]), errorContainer);
	};
	form.on('hidden', function() {
		// tidy up in case closed before completed
		SolarNode.hideLoading(installBtn);
		progressBar.addClass('hide');
		installBtn.removeClass('hide');
		
		// refresh the plugin list, if we've installed/removed anything
		if ( refreshPluginListOnModalClose === true ) {
			SolarNode.Plugins.populateUI($('#plugins'));
		}
		
		// in case we were still polling when close... don't bother to keep going
		keepPollingForStatus = false;
	});
	form.ajaxForm({
		dataType: 'json',
		beforeSubmit: function(dataArray, form, options) {
			// start a progress bar on the install button so we know a install is happening
			progressBar.removeClass('hide');
			progressFill.css('width', '0%');
			errorContainer.empty();
			SolarNode.showLoading(installBtn);
		},
		success: function(json, status, xhr, form) {
			if ( json.success !== true ) {
				SolarNode.hideLoading(installBtn);
				showAlert(json.message);
				return;
			}
			// TODO: support message? var message = json.data.statusMessage;
			var progress = Math.round(json.data.overallProgress * 100);
			var pollURL = SolarNode.context.path('/plugins/provisionStatus') +'?id=' 
					+encodeURIComponent(json.data.provisionID) +'&p=';
			(function poll() {
			    $.ajax({ 
			    	url: (pollURL + progress),
			    	dataType: "json",
			    	success: function(json) {
			    		if ( json.success === true && json.data !== undefined ) {
			    			progress = Math.round(json.data.overallProgress * 100);
			    			progressFill.css('width', progress +'%');
			    		} else {
			    			if ( json.message !== undefined ) {
			    				showAlert(json.message);
			    			}
			    			keepPollingForStatus = false;
			    		}
			    	}, 
			    	complete: function(xhr, status) {
			    		if ( status === 'error' ) {
			    			showAlert(xhr.statusText);
			    		} else if ( !(progress < 100) ) {
							SolarNode.hideLoading(installBtn);
			    			progressBar.addClass('hide');
			    			installBtn.addClass('hide');
			    			SolarNode.info(SolarNode.i18n(installBtn.data('msg-success')), errorContainer);
			    			refreshPluginListOnModalClose = true;
			    		} else if ( keepPollingForStatus ) {
			    			poll();
			    		}
			    	}, 
			    	timeout: 20000,
			    });
			})();
		},
		error: function(xhr, status, statusText) {
			SolarNode.hideLoading(installBtn);
			showAlert(statusText);
		}
	});
};

$(document).ready(function() {
	var pluginsContainer = $('#plugins').first();
	pluginsContainer.each(function() {
		SolarNode.Plugins.populateUI($(this));
	});
	$('#plugins-refresh').click(function(event) {
		event.preventDefault();
		SolarNode.Plugins.refreshPluginList($(this).attr('href'), pluginsContainer);
	});
	$('#plugin-preview-install-modal').first().each(function() {
		SolarNode.Plugins.handleInstall($(this));
	});
});
