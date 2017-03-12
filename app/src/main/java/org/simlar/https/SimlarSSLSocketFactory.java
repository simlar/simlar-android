/**
 * Copyright (C) 2013 The Simlar Authors.
 *
 * This file is part of Simlar. (https://www.simlar.org)
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

package org.simlar.https;

import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.simlar.helper.FileHelper;
import org.simlar.logging.Lg;

@SuppressWarnings("Singleton")
final class SimlarSSLSocketFactory extends SSLSocketFactory
{
	private static final String[] PREFERRED_CIPHER_SUITES = { "TLS_DHE_RSA_WITH_AES_256_CBC_SHA", "DHE-RSA-AES256-SHA" };
	private static final String[] PREFERRED_PROTOCOLS = { "TLSv1.3", "TLSv1.2", "TLSv1.1", "TLSv1" };

	private static final String[] CIPHER_SUITES = createCipherSuites();
	private static final String[] PROTOCOLS = createProtocols();

	private final SSLSocketFactory mSSLSocketFactory;

	// thread-safe and lazy evaluation singleton as proposed by Bill Pugh
	private static final class InstanceHolder
	{
		static final SimlarSSLSocketFactory INSTANCE = new SimlarSSLSocketFactory();
	}

	public static SimlarSSLSocketFactory getInstance()
	{
		return InstanceHolder.INSTANCE;
	}

	private SimlarSSLSocketFactory()
	{
		mSSLSocketFactory = createSSLSocketFactory();
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
			final TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmAlgorithm);
			tmf.init(keyStore);

			// Create an SSLContext that uses our TrustManager
			final SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, tmf.getTrustManagers(), null);
			return context.getSocketFactory();
		} catch (final CertificateException | NoSuchAlgorithmException | IOException | KeyStoreException | KeyManagementException e) {
			// We expect Simlar to crash here as SSL connections are essential.
			throw new RuntimeException("unable to create SSL socket", e);
		}
	}

	private static String[] getPreferred(final String[] preferred, final String[] supported, final String[] defaults)
	{
		final List<String> sup = Arrays.asList(supported);

		for (final String pref : preferred) {
			if (sup.contains(pref)) {
				return new String[] { pref };
			}
		}

		return defaults;
	}

	private static String[] createCipherSuites()
	{
		final String[] cipherSuites = getPreferred(PREFERRED_CIPHER_SUITES,
				HttpsURLConnection.getDefaultSSLSocketFactory().getSupportedCipherSuites(),
				HttpsURLConnection.getDefaultSSLSocketFactory().getDefaultCipherSuites());
		Lg.i("using cipher suites: ", TextUtils.join(", ", cipherSuites));
		return cipherSuites;
	}

	private static String[] createProtocols()
	{
		try {
			final String[] deviceSupports = ((SSLSocket) HttpsURLConnection.getDefaultSSLSocketFactory().createSocket()).getSupportedProtocols();
			final String[] protocols = getPreferred(PREFERRED_PROTOCOLS, deviceSupports, deviceSupports);
			Lg.i("using protocols: ", TextUtils.join(", ", protocols));
			return protocols;
		} catch (final IOException e) {
			Lg.ex(e, "failed to create protocols");
			return PREFERRED_PROTOCOLS;
		}
	}

	private static Certificate loadCertificate()
	{
		if (!FileHelper.isInitialized()) {
			Lg.e("Error: FileHelper not initialized");
			return null;
		}

		InputStream caInput = null;
		try {
			final CertificateFactory cf = CertificateFactory.getInstance("X.509");
			caInput = new BufferedInputStream(new FileInputStream(FileHelper.getRootCaFileName()));
			return cf.generateCertificate(caInput);
		} catch (final CertificateException e) {
			Lg.ex(e, "CertificateException during loadCertificate");
			return null;
		} catch (final FileNotFoundException e) {
			Lg.ex(e, "FileNotFoundException during loadCertificate with file=", FileHelper.getRootCaFileName());
			return null;
		} finally {
			try {
				if (caInput != null) {
					caInput.close();
				}
			} catch (final IOException e) {
				Lg.ex(e, "IOException during loadCertificate");
			}
		}
	}

	@Override
	public String[] getDefaultCipherSuites()
	{
		return Arrays.copyOf(CIPHER_SUITES, CIPHER_SUITES.length);
	}

	@Override
	public String[] getSupportedCipherSuites()
	{
		return Arrays.copyOf(CIPHER_SUITES, CIPHER_SUITES.length);
	}

	@Override
	public Socket createSocket(final Socket s, final String host, final int port, final boolean autoClose) throws IOException
	{
		final SSLSocket socket = (SSLSocket) mSSLSocketFactory.createSocket(s, host, port, autoClose);
		socket.setEnabledCipherSuites(CIPHER_SUITES);
		socket.setEnabledProtocols(PROTOCOLS);
		return socket;
	}

	@Override
	public Socket createSocket(final String host, final int port) throws IOException
	{
		final SSLSocket socket = (SSLSocket) mSSLSocketFactory.createSocket(host, port);
		socket.setEnabledCipherSuites(CIPHER_SUITES);
		socket.setEnabledProtocols(PROTOCOLS);
		return socket;
	}

	@Override
	public Socket createSocket(final String host, final int port, final InetAddress localHost, final int localPort) throws IOException
	{
		final SSLSocket socket = (SSLSocket) mSSLSocketFactory.createSocket(host, port, localHost, localPort);
		socket.setEnabledCipherSuites(CIPHER_SUITES);
		socket.setEnabledProtocols(PROTOCOLS);
		return socket;
	}

	@Override
	public Socket createSocket(final InetAddress host, final int port) throws IOException
	{
		final SSLSocket socket = (SSLSocket) mSSLSocketFactory.createSocket(host, port);
		socket.setEnabledCipherSuites(CIPHER_SUITES);
		socket.setEnabledProtocols(PROTOCOLS);
		return socket;
	}

	@Override
	public Socket createSocket(final InetAddress address, final int port, final InetAddress localAddress, final int localPort) throws IOException
	{
		final SSLSocket socket = (SSLSocket) mSSLSocketFactory.createSocket(address, port, localAddress, localPort);
		socket.setEnabledCipherSuites(CIPHER_SUITES);
		socket.setEnabledProtocols(PROTOCOLS);
		return socket;
	}
}
