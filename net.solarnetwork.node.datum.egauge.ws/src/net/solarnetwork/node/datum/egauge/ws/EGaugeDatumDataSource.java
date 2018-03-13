/* ==================================================================
 * EGaugeXMLDatumDataSource.java - Oct 2, 2011 8:50:13 PM
 * 
 * Copyright 2007-2011 SolarNetwork.net Dev Team
 * 
 * This program is free software; you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as 
 * published by the Free Software Foundation; either version 2 of 
 * the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with this program; if not, write to the Free Software 
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 
 * 02111-1307 USA
 * ==================================================================
 */

package net.solarnetwork.node.datum.egauge.ws;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.solarnetwork.node.DatumDataSource;
import net.solarnetwork.node.datum.egauge.ws.client.EGaugeClient;
import net.solarnetwork.node.datum.egauge.ws.client.XmlEGaugeClient;
import net.solarnetwork.node.domain.GeneralNodePVEnergyDatum;
import net.solarnetwork.node.settings.SettingSpecifier;
import net.solarnetwork.node.settings.SettingSpecifierProvider;
import net.solarnetwork.node.settings.support.BasicTextFieldSettingSpecifier;
import net.solarnetwork.node.settings.support.BasicTitleSettingSpecifier;
import net.solarnetwork.node.support.DatumDataSourceSupport;
import net.solarnetwork.util.CachedResult;

/**
 * Web service based support for eGauge inverters. Needs to be configured with
 * an {@link EGaugeClient} such as {@link XmlEGaugeClient} to retrieve the
 * content to be stored.
 * 
 * <p>
 * If the {@code client} configuration is the same, it should be possible to
 * share a single client between multiple instances and just configure the
 * {@code host} and {@code sourceId} properties to use different sources.
 * </p>
 * 
 * @author maxieduncan
 * @version 1.0
 */
public class EGaugeDatumDataSource extends DatumDataSourceSupport
		implements DatumDataSource<GeneralNodePVEnergyDatum>, SettingSpecifierProvider {

	/** The host to get the {@code client} to retrieve the eGauge data from. */
	private String host;
	/** The ID that identifies the source. */
	private String sourceId;
	/**
	 * The client that should be used to retrieve the eGauge data from the
	 * {@code host}.
	 */
	private EGaugeClient client;
	/** The time that the results should be cached in milliseconds. */
	private long sampleCacheMs = 5000;
	/**
	 * Used to store error details when the client fails to access eGauge
	 * content.
	 */
	private Throwable sampleException;

	private AtomicReference<CachedResult<EGaugePowerDatum>> sampleCache = new AtomicReference<>();
	private final Map<String, Long> validationCache = new HashMap<String, Long>(4);

	private EGaugePowerDatum getCurrentSample() {
		// First check for a cached sample
		CachedResult<EGaugePowerDatum> cache = sampleCache.get();
		if ( cache != null && cache.isValid() ) {
			return cache.getResult();
		}

		// Cache has expired so initiate new instance and cache
		EGaugePowerDatum datum = null;
		try {
			datum = getClient().getCurrent(getHost(), getSourceId());
		} catch ( RuntimeException e ) {
			Throwable root = e;
			while ( root.getCause() != null ) {
				root = root.getCause();
			}

			// Keep track of the root exception for reporting
			sampleException = root;
			throw e;
		}

		if ( datum != null ) {
			setSampleCache(datum);
		}
		return datum;
	}

	//	

	private void setSampleCache(EGaugePowerDatum datum) {
		sampleCache.set(new CachedResult<EGaugePowerDatum>(datum, sampleCacheMs, TimeUnit.MILLISECONDS));
	}

	@Override
	public String toString() {
		return "EGaugeDatumDataSource [host=" + host + ", sourceId=" + sourceId + ", client=" + client
				+ "]";
	}

	@Override
	public Class<? extends GeneralNodePVEnergyDatum> getDatumType() {
		return EGaugePowerDatum.class;
	}

	@Override
	public EGaugePowerDatum readCurrentDatum() {
		return getCurrentSample();
	}

	@Override
	public String getUID() {
		return getSourceId();
	}

	public void init() {
		// Nothing to do currently
	}

	@Override
	public String getSettingUID() {
		return "net.solarnetwork.node.datum.egauge.ws";
	}

	@Override
	public String getDisplayName() {
		return "eGauge web service data source";
	}

	@Override
	public List<SettingSpecifier> getSettingSpecifiers() {
		EGaugeDatumDataSource defaults = new EGaugeDatumDataSource();
		List<SettingSpecifier> results = new ArrayList<SettingSpecifier>(10);
		results.add(new BasicTitleSettingSpecifier("info", getInfoMessage(), true));
		results.add(new BasicTextFieldSettingSpecifier("host", ""));
		results.add(new BasicTextFieldSettingSpecifier("sourceId", ""));
		results.add(new BasicTextFieldSettingSpecifier("groupUID", null));
		results.add(new BasicTextFieldSettingSpecifier("sampleCacheMs",
				String.valueOf(defaults.sampleCacheMs)));
		return results;
	}

	/**
	 * Get an informational status message.
	 * 
	 * @return A status message.
	 */
	public String getInfoMessage() {
		EGaugePowerDatum snap = null;
		try {
			snap = getCurrentSample();
		} catch ( Exception e ) {
			// we must ignore exceptions here
		}
		StringBuilder buf = new StringBuilder();
		Throwable t = sampleException;
		if ( t != null ) {
			buf.append("Error communicating with eGauge inverter: ").append(t.getMessage());
		}
		if ( snap != null ) {
			if ( buf.length() > 0 ) {
				buf.append("; ");
			}
			buf.append(snap.getSolarPlusWatts()).append(" W; ");
			buf.append(snap.getSolarPlusWattHourReading()).append(" Wh; Solar+ sample created ");
			buf.append(snap.getGridWatts()).append(" W; ");
			buf.append(snap.getGridWattHourReading()).append(" Wh; Grid sample created ");
			buf.append(String.format("%tc", snap.getCreated()));
		}
		return (buf.length() < 1 ? "N/A" : buf.toString());
	}

	/**
	 * Get the configured source ID.
	 * 
	 * @return the source ID
	 */
	public String getSourceId() {
		return sourceId;
	}

	/**
	 * Set the source ID value to assign to the collected data.
	 * 
	 * @param sourceId
	 *        the source ID to set
	 */
	public void setSourceId(String sourceId) {
		this.sourceId = sourceId;
		validationCache.clear();
	}

	public void setSampleCacheMs(long sampleCacheMs) {
		this.sampleCacheMs = sampleCacheMs;
	}

	public EGaugeClient getClient() {
		return client;
	}

	public void setClient(EGaugeClient client) {
		this.client = client;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

}
