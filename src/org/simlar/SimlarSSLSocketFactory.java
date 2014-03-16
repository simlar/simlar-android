/**
 * Copyright (C) 2013 The Simlar Authors.
 *
 * This file is part of Simlar. (http://www.simlar.org)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.simlar;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import android.text.TextUtils;
import android.util.Log;

public class SimlarSSLSocketFactory extends SSLSocketFactory
{
	private static final String LOGTAG = SimlarSSLSocketFactory.class.getSimpleName();

	private static final String[] PREFERRED_CYPHPER_SUITES = { "TLS_DHE_RSA_WITH_AES_256_CBC_SHA", "DHE-RSA-AES256-SHA" };
	private static final String[] PREFERRED_PROTOCOLS = { "TLSv1.2", "TLSv1.1", "TLSv1" };

	private static final String[] CYPHPER_SUITES = createCypherSuite();
	private static final String[] PROTOCOLS = createProtocols();

	private SSLSocketFactory mSSLSocketFactory = createSSLSocketFactory();

	// thread-safe and lazy evaluation singleton as proposed by Bill Pugh
	private static final class InstanceHolder
	{
		@SuppressWarnings("synthetic-access")
		static final SimlarSSLSocketFactory INSTANCE = new SimlarSSLSocketFactory();
	}

	public static SimlarSSLSocketFactory getInstance()
	{
		return InstanceHolder.INSTANCE;
	}

	private static String[] getPreferred(final String[] preferred, final String[] supported, final String[] defaults)
	{
		final List<String> sup = Arrays.asList(supported);

		for (final String pref : preferred) {
			if (sup.contains(pref)) {
				final String[] retVal = { pref };
				return retVal;
			}
		}

		return defaults;
	}

	private static String[] createCypherSuite()
	{
		final String[] cypherSuites = getPreferred(PREFERRED_CYPHPER_SUITES,
				HttpsURLConnection.getDefaultSSLSocketFactory().getSupportedCipherSuites(),
				HttpsURLConnection.getDefaultSSLSocketFactory().getDefaultCipherSuites());
		Log.i(LOGTAG, "using cipher suites: " + TextUtils.join(", ", cypherSuites));
		return cypherSuites;
	}

	private static String[] createProtocols()
	{
		try {
			final String[] deviceSupports = ((SSLSocket) HttpsURLConnection.getDefaultSSLSocketFactory().createSocket()).getSupportedProtocols();
			final String[] protocols = getPreferred(PREFERRED_PROTOCOLS, deviceSupports, deviceSupports);
			Log.i(LOGTAG, "using protocols: " + TextUtils.join(", ", protocols));
			return protocols;
		} catch (IOException e) {
			Log.e(LOGTAG, "failed to create protocols: " + e.getMessage(), e);
			return null;
		}
	}

	private static Certificate loadCertificate()
	{
		if (!FileHelper.isInitialized()) {
			Log.e(LOGTAG, "Error: FileHelper not initialized");
			return null;
		}

		InputStream caInput = null;
		try {
			final CertificateFactory cf = CertificateFactory.getInstance("X.509");
			caInput = new BufferedInputStream(new FileInputStream(FileHelper.getRootCaFileName()));
			return cf.generateCertificate(caInput);
		} catch (Exception e) {
			Log.e(LOGTAG, "Exception during loadCertificate: " + e.getMessage(), e);
			return null;
		} finally {
			try {
				if (caInput != null) {
					caInput.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static SSLSocketFactory createSSLSocketFactory()
	{
		final Certificate ca = loadCertificate();

		try {
			final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			keyStore.load(null, null);
			keyStore.setCertificateEntry("SimlarCA", ca);

			// Create a TrustManager that trusts the CAs in our KeyStore
			final String tmAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmAlgorithm);
			tmf.init(keyStore);

			// Create an SSLContext that uses our TrustManager
			final SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, tmf.getTrustManagers(), null);
			return context.getSocketFactory();
		} catch (Exception e) {
			Log.e(LOGTAG, "Exception during createSSLSocketFactory: " + e.getMessage(), e);
			return null;
		}
	}

	private SimlarSSLSocketFactory()
	{
		super();
	}

	@Override
	public String[] getDefaultCipherSuites()
	{
		return CYPHPER_SUITES;
	}

	@Override
	public String[] getSupportedCipherSuites()
	{
		return CYPHPER_SUITES;
	}

	@Override
	public Socket createSocket(final Socket s, final String host, final int port, final boolean autoClose) throws IOException
	{
		final SSLSocket socket = (SSLSocket) mSSLSocketFactory.createSocket(s, host, port, autoClose);
		socket.setEnabledCipherSuites(CYPHPER_SUITES);
		socket.setEnabledProtocols(PROTOCOLS);
		return socket;
	}

	@Override
	public Socket createSocket(final String host, final int port) throws IOException, UnknownHostException
	{
		final SSLSocket socket = (SSLSocket) mSSLSocketFactory.createSocket(host, port);
		socket.setEnabledCipherSuites(CYPHPER_SUITES);
		socket.setEnabledProtocols(PROTOCOLS);
		return socket;
	}

	@Override
	public Socket createSocket(final String host, final int port, final InetAddress localHost, final int localPort) throws IOException,
			UnknownHostException
	{
		final SSLSocket socket = (SSLSocket) mSSLSocketFactory.createSocket(host, port, localHost, localPort);
		socket.setEnabledCipherSuites(CYPHPER_SUITES);
		socket.setEnabledProtocols(PROTOCOLS);
		return socket;
	}

	@Override
	public Socket createSocket(final InetAddress host, final int port) throws IOException
	{
		final SSLSocket socket = (SSLSocket) mSSLSocketFactory.createSocket(host, port);
		socket.setEnabledCipherSuites(CYPHPER_SUITES);
		socket.setEnabledProtocols(PROTOCOLS);
		return socket;
	}

	@Override
	public Socket createSocket(final InetAddress address, final int port, final InetAddress localAddress, final int localPort) throws IOException
	{
		final SSLSocket socket = (SSLSocket) mSSLSocketFactory.createSocket(address, port, localAddress, localPort);
		socket.setEnabledCipherSuites(CYPHPER_SUITES);
		socket.setEnabledProtocols(PROTOCOLS);
		return socket;
	}

}
