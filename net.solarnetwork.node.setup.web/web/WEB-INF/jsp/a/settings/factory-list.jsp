<section class="intro">
	<h2>
		<fmt:message key="settings.factory.title">
			<fmt:param><setup:message key="title" messageSource="${factory.messageSource}" text="${factory.displayName}"/></fmt:param>
		</fmt:message>
	</h2>
	<p>
		<fmt:message key="settings.factory.intro">
			<fmt:param><setup:message key="title" messageSource="${factory.messageSource}" text="${factory.displayName}"/></fmt:param>
		</fmt:message>
	</p>
	<p>
		<a href="<c:url value='/settings.do'/>" class="btn">
			<i class="icon-arrow-left"></i>
			<fmt:message key="back.label"/>
		</a>
		<button type="button" class="btn btn-primary" id="add">
			<i class="icon-plus icon-white"></i>
			<fmt:message key='settings.factory.add'>
				<fmt:param><setup:message key="title" messageSource="${factory.messageSource}" text="${factory.displayName}"/></fmt:param>
			</fmt:message>
		</button>
	</p>
</section>

<section id="settings">
	<form class="form-horizontal" action="<c:url value='/settings/save.do'/>" method="post">
		<c:forEach items="${providers}" var="instance" varStatus="instanceStatus">
			<c:set var="instance" value="${instance}" scope="request"/>
			<c:forEach items="${instance.value}" var="provider" varStatus="providerStatus">
				<c:set var="provider" value="${provider}" scope="request"/>
				<c:set var="instanceId" value="${provider.factoryInstanceUID}" scope="request"/>
				<!--  ${provider.settingUID} -->
		
					<fieldset>
						<legend>
							<setup:message key="title" messageSource="${factory.messageSource}" text="${factory.displayName}"/>
							${' '}
							${instance.key}
						</legend>
						
						<c:forEach items="${provider.settingSpecifiers}" var="setting" varStatus="settingStatus">
							<c:set var="setting" value="${setting}" scope="request"/>
							<c:set var="settingId" value="m${instanceStatus.index}s${providerStatus.index}i${settingStatus.index}" scope="request"/>
							<c:import url="/WEB-INF/jsp/a/settings/setting-control.jsp"/>
						</c:forEach>
						<div class="control-group">
							<div class="controls">
								<button type="button" class="btn btn-danger" id="del${instance.key}">
									<fmt:message key='settings.factory.delete'>
										<fmt:param><setup:message key="title" messageSource="${factory.messageSource}" text="${factory.displayName}"/></fmt:param>
										<fmt:param value="${instance.key}"/>
									</fmt:message>
								</button>
								<script>
								$('#del${instance.key}').click(function() {
									SolarNode.Settings.deleteFactoryConfiguration({
										button: this,
										url: '<c:url value="/settings/manage/delete.do"/>',
										factoryUID: '${factory.factoryUID}',
										instanceUID: '${instance.key}'
									});
								});
								</script>
							</div>
						</div>
					</fieldset>
			</c:forEach>
		</c:forEach>
		<div class="form-actions">
			<button type="button" class="btn btn-primary" id="submit"><fmt:message key='settings.save'/></button>
		</div>
	</form>
</section>
<script>
$(function() {
	$('#submit').click(function() {
		SolarNode.Settings.saveUpdates($(this.form).attr('action'), {
			success: '<fmt:message key="settings.save.success.msg"/>',
			error: '<fmt:message key="settings.save.error.msg"/>',
			title: '<fmt:message key="settings.save.result.title"/>',
			button: '<fmt:message key="ok.label"/>'
		});
	});
	$('#add').click(function() {
		SolarNode.Settings.addFactoryConfiguration({
			button: this,
			url: '<c:url value="/settings/manage/add.do"/>',
			factoryUID: '${factory.factoryUID}'
		});
	});
	SolarNode.Settings.reset();
});
</script>
<div id="alert-delete" class="alert alert-danger alert-block hidden">
	<button type="button" class="close" data-dismiss="alert">�</button>
	<h4><fmt:message key="settings.factory.delete.alert.title"/></h4>
	<p>
		<fmt:message key="settings.factory.delete.alert.msg"/>
	</p>
	<button type="button" class="btn btn-danger submit">
		<fmt:message key="delete.label"/>
	</button>
</div>
<form class="modal dynamic hide fade lookup-modal price-lookup-modal" 
		action="<c:url value='/api/v1/sec/location'/>" method="get">
	<div class="modal-header">
		<button type="button" class="close" data-dismiss="modal">&times;</button>
		<h3><fmt:message key='lookup.price.title'/></h3>
	</div>
	<div class="modal-body">
		<p><fmt:message key='lookup.price.intro'/></p>
		<div class="form-inline">
			<input type="hidden" name="tags" value="price"/>
			<input type="text" class="span4" maxlength="64" name="query" placeholder="<fmt:message key='lookup.price.locationName'/>"/>
			<button type="submit" class="btn btn-primary ladda-button expand-right" data-loading-text="<fmt:message key='lookup.searching.label'/>">
				<fmt:message key='lookup.action.search'/>
			</button>
		</div>

		<table class="table table-striped table-hover hidden search-results">
			<thead>
				<tr>
					<th><fmt:message key='lookup.price.sourceName'/></th>
					<th><fmt:message key='lookup.price.locationName'/></th>
					<th><fmt:message key='lookup.price.currency'/></th>
				</tr>
				<tr class="template">
					<td data-tprop="sourceId"></td>
					<td data-tprop="m.name"></td>
					<td data-tprop="m.currency"></td>
				</tr>
			</thead>
			<tbody>
			</tbody>
		</table>
		
		<label id="price-lookup-selected-label" class="hidden">
			<fmt:message key='lookup.selected.label'/>
			<span id="price-lookup-selected-container"></span>
		</label>
	</div>
	<div class="modal-footer">
		<a href="#" class="btn" data-dismiss="modal"><fmt:message key='close.label'/></a>
		<button type="button" class="btn btn-primary choose" disabled="disabled">
			<fmt:message key="lookup.action.choose"/>
		</button>
	</div>
</form>
<form class="modal dynamic hide fade lookup-modal weather-lookup-modal day-lookup-modal" 
		action="<c:url value='/api/v1/sec/location'/>" method="get">
	<div class="modal-header">
		<button type="button" class="close" data-dismiss="modal">&times;</button>
		<h3><fmt:message key='lookup.weather.title'/></h3>
	</div>
	<div class="modal-body">
		<p><fmt:message key='lookup.weather.intro'/></p>
		<div class="form-inline">
			<input type="hidden" name="tags" value="weather"/>
			<input type="text" class="span4" maxlength="64" name="location.region" placeholder="<fmt:message key='lookup.weather.search.placeholder'/>"/>
			<button type="submit" class="btn btn-primary ladda-button expand-right" 
				data-loading-text="<fmt:message key='lookup.searching.label'/>">
				<fmt:message key='lookup.action.search'/>
			</button>
		</div>

		<table class="table table-striped table-hover hidden search-results">
			<thead>
				<tr>
					<th><fmt:message key='lookup.weather.sourceName'/></th>
					<th><fmt:message key='lookup.weather.country'/></th>
					<th><fmt:message key='lookup.weather.region'/></th>
					<th><fmt:message key='lookup.weather.locality'/></th>
					<th><fmt:message key='lookup.weather.postalCode'/></th>
				</tr>
				<tr class="template">
					<td data-tprop="sourceId"></td>
					<td data-tprop="location.country"></td>
					<td data-tprop="location.region"></td>
					<td data-tprop="location.locality"></td>
					<td data-tprop="location.postalCode"></td>
				</tr>
			</thead>
			<tbody>
			</tbody>
		</table>
		
		<label id="weather-lookup-selected-label" class="hidden">
			<fmt:message key='lookup.selected.label'/>
			<span id="weather-lookup-selected-container"></span>
		</label>
	</div>
	<div class="modal-footer">
		<a href="#" class="btn" data-dismiss="modal"><fmt:message key='close.label'/></a>
		<button type="button" class="btn btn-primary choose" disabled="disabled">
			<fmt:message key="lookup.action.choose"/>
		</button>
	</div>
</form>
