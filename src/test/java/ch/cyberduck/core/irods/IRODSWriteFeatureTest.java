package ch.cyberduck.core.irods;

/*
 * Copyright (c) 2002-2015 David Kocher. All rights reserved.
 * http://cyberduck.ch/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Bug fixes, suggestions and comments should be sent to feedback@cyberduck.ch
 */

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import ch.cyberduck.core.Credentials;
import ch.cyberduck.core.DisabledCancelCallback;
import ch.cyberduck.core.DisabledHostKeyCallback;
import ch.cyberduck.core.DisabledListProgressListener;
import ch.cyberduck.core.DisabledLoginCallback;
import ch.cyberduck.core.DisabledPasswordStore;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.Local;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathAttributes;
import ch.cyberduck.core.PathCache;
import ch.cyberduck.core.Profile;
import ch.cyberduck.core.ProfileReaderFactory;
import ch.cyberduck.core.ProtocolFactory;
import ch.cyberduck.core.features.Delete;
import ch.cyberduck.core.features.Find;
import ch.cyberduck.core.features.Read;
import ch.cyberduck.core.io.StreamCopier;
import ch.cyberduck.core.transfer.TransferStatus;
import ch.cyberduck.test.IntegrationTest;

@Category(IntegrationTest.class)
public class IRODSWriteFeatureTest {

	@BeforeClass
	public static void protocol() {
		ProtocolFactory.register(new IRODSProtocol());
	}

	@Test
	public void testWrite() throws Exception {
		final Profile profile = ProfileReaderFactory.get().read(
				new Local("/home/mconway/testprofile"));
		final Host host = new Host(profile, profile.getDefaultHostname(),
				new Credentials("test1", "test"));

		// MCC
		// HostPasswordStore pwdStore = PasswordStoreFactory.get();
		// pwdStore.

		final IRODSSession session = new IRODSSession(host);
		session.open(new DisabledHostKeyCallback());
		session.login(new DisabledPasswordStore(), new DisabledLoginCallback(),
				new DisabledCancelCallback(), PathCache.empty());

		final Path test = new Path(new IRODSHomeFinderService(session).find(),
				UUID.randomUUID().toString(), EnumSet.of(Path.Type.file));
		assertFalse(session.getFeature(Find.class).find(test));

		final byte[] content = RandomStringUtils.random(100).getBytes();
		{
			final TransferStatus status = new TransferStatus();
			status.setAppend(false);
			status.setLength(content.length);

			assertEquals(
					false,
					new IRODSWriteFeature(session).append(test,
							status.getLength(), PathCache.empty()).append);
			assertEquals(
					0L,
					new IRODSWriteFeature(session).append(test,
							status.getLength(), PathCache.empty()).size, 0L);

			final OutputStream out = new IRODSWriteFeature(session).write(test,
					status);
			assertNotNull(out);

			new StreamCopier(new TransferStatus(), new TransferStatus())
					.transfer(new ByteArrayInputStream(content), out);
			out.close();
			assertTrue(session.getFeature(Find.class).find(test));

			final PathAttributes attributes = session
					.list(test.getParent(), new DisabledListProgressListener())
					.get(test).attributes();
			assertEquals(content.length, attributes.getSize());

			final InputStream in = session.getFeature(Read.class).read(test,
					new TransferStatus());
			final byte[] buffer = new byte[content.length];
			IOUtils.readFully(in, buffer);
			in.close();
			assertArrayEquals(content, buffer);
		}
		{
			final byte[] newcontent = RandomStringUtils.random(10).getBytes();

			final TransferStatus status = new TransferStatus();
			status.setAppend(false);
			status.setLength(newcontent.length);

			assertEquals(
					true,
					new IRODSWriteFeature(session).append(test,
							status.getLength(), PathCache.empty()).append);
			assertEquals(
					content.length,
					new IRODSWriteFeature(session).append(test,
							status.getLength(), PathCache.empty()).size, 0L);

			final OutputStream out = new IRODSWriteFeature(session).write(test,
					status);
			assertNotNull(out);

			new StreamCopier(new TransferStatus(), new TransferStatus())
					.transfer(new ByteArrayInputStream(newcontent), out);
			out.close();
			assertTrue(session.getFeature(Find.class).find(test));

			final PathAttributes attributes = session
					.list(test.getParent(), new DisabledListProgressListener())
					.get(test).attributes();
			assertEquals(newcontent.length, attributes.getSize());

			final InputStream in = session.getFeature(Read.class).read(test,
					new TransferStatus());
			final byte[] buffer = new byte[newcontent.length];
			IOUtils.readFully(in, buffer);
			in.close();
			assertArrayEquals(newcontent, buffer);
		}

		session.getFeature(Delete.class).delete(Arrays.asList(test),
				new DisabledLoginCallback(), new Delete.DisabledCallback());
		assertFalse(session.getFeature(Find.class).find(test));
		session.close();
	}

	@Test
	public void testWriteConcurent() throws Exception {
		final Profile profile = ProfileReaderFactory.get().read(
				new Local("/home/mconway/testprofile"));
		final Host host = new Host(profile, profile.getDefaultHostname(),
				new Credentials("test1", "test"));

		final IRODSSession session1 = new IRODSSession(host);
		session1.open(new DisabledHostKeyCallback());

		session1.login(new DisabledPasswordStore(),
				new DisabledLoginCallback(), new DisabledCancelCallback(),
				PathCache.empty());

		final IRODSSession session2 = new IRODSSession(host);
		session2.open(new DisabledHostKeyCallback());

		session2.login(new DisabledPasswordStore(),
				new DisabledLoginCallback(), new DisabledCancelCallback(),
				PathCache.empty());

		final Path test1 = new Path(
				new IRODSHomeFinderService(session1).find(), UUID.randomUUID()
						.toString(), EnumSet.of(Path.Type.file));
		final Path test2 = new Path(
				new IRODSHomeFinderService(session2).find(), UUID.randomUUID()
						.toString(), EnumSet.of(Path.Type.file));

		final byte[] content = RandomUtils.nextBytes(68400);

		final OutputStream out1 = new IRODSWriteFeature(session1).write(test1,
				new TransferStatus().append(false).length(content.length));
		final OutputStream out2 = new IRODSWriteFeature(session1).write(test2,
				new TransferStatus().append(false).length(content.length));
		new StreamCopier(new TransferStatus(), new TransferStatus()).transfer(
				new ByteArrayInputStream(content), out2);
		out2.close();
		new StreamCopier(new TransferStatus(), new TransferStatus()).transfer(
				new ByteArrayInputStream(content), out1);
		out1.close();

		session1.close();
		session2.close();
	}

	@Test
	public void testWriteAppend() throws Exception {
		final Profile profile = ProfileReaderFactory.get().read(
				new Local("/home/mconway/testprofile"));
		final Host host = new Host(profile, profile.getDefaultHostname(),
				new Credentials("test1", "test"));
		final IRODSSession session = new IRODSSession(host);
		session.open(new DisabledHostKeyCallback());
		session.login(new DisabledPasswordStore(), new DisabledLoginCallback(),
				new DisabledCancelCallback(), PathCache.empty());

		final Path test = new Path(new IRODSHomeFinderService(session).find(),
				UUID.randomUUID().toString(), EnumSet.of(Path.Type.file));
		assertFalse(session.getFeature(Find.class).find(test));

		final byte[] content = RandomStringUtils.random(
				(int) (Math.random() * 100)).getBytes();

		final TransferStatus status = new TransferStatus();
		status.setAppend(true);
		status.setLength(content.length);

		assertEquals(false, new IRODSWriteFeature(session).append(test,
				status.getLength(), PathCache.empty()).append);
		assertEquals(0L, new IRODSWriteFeature(session).append(test,
				status.getLength(), PathCache.empty()).size, 0L);

		final OutputStream out = new IRODSWriteFeature(session).write(test,
				status);
		assertNotNull(out);

		new StreamCopier(new TransferStatus(), new TransferStatus()).transfer(
				new ByteArrayInputStream(content), out);
		out.close();
		assertTrue(session.getFeature(Find.class).find(test));

		final PathAttributes attributes = session
				.list(test.getParent(), new DisabledListProgressListener())
				.get(test).attributes();
		assertEquals(content.length, attributes.getSize());

		final InputStream in = session.getFeature(Read.class).read(test,
				new TransferStatus());
		final byte[] buffer = new byte[content.length];
		IOUtils.readFully(in, buffer);
		in.close();
		assertArrayEquals(content, buffer);

		// Append

		final byte[] content_append = RandomStringUtils.random(
				(int) (Math.random() * 100)).getBytes();

		final TransferStatus status_append = new TransferStatus();
		status_append.setAppend(true);
		status_append.setLength(content_append.length);

		assertEquals(
				true,
				new IRODSWriteFeature(session).append(test,
						status_append.getLength(), PathCache.empty()).append);
		assertEquals(
				status.getLength(),
				new IRODSWriteFeature(session).append(test,
						status_append.getLength(), PathCache.empty()).size, 0L);

		final OutputStream out_append = new IRODSWriteFeature(session).write(
				test, status_append);
		assertNotNull(out_append);

		new StreamCopier(new TransferStatus(), new TransferStatus()).transfer(
				new ByteArrayInputStream(content_append), out_append);
		out_append.close();
		assertTrue(session.getFeature(Find.class).find(test));

		final PathAttributes attributes_complete = session
				.list(test.getParent(), new DisabledListProgressListener())
				.get(test).attributes();
		assertEquals(content.length + content_append.length,
				attributes_complete.getSize());

		final InputStream in_append = session.getFeature(Read.class).read(test,
				new TransferStatus());
		final byte[] buffer_complete = new byte[content.length
				+ content_append.length];
		IOUtils.readFully(in_append, buffer_complete);
		in_append.close();

		byte[] complete = new byte[content.length + content_append.length];
		System.arraycopy(content, 0, complete, 0, content.length);
		System.arraycopy(content_append, 0, complete, content.length,
				content_append.length);
		assertArrayEquals(complete, buffer_complete);

		session.getFeature(Delete.class).delete(Arrays.asList(test),
				new DisabledLoginCallback(), new Delete.DisabledCallback());
		assertFalse(session.getFeature(Find.class).find(test));
		session.close();
	}
}
