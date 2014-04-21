package com.netflix.nicobar.cassandra;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.fail;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import org.apache.commons.io.IOUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.nicobar.cassandra.CassandraArchiveRepository.Columns;
import com.netflix.nicobar.core.archive.JarScriptArchive;
import com.netflix.nicobar.core.archive.ModuleId;
import com.netflix.nicobar.core.archive.ScriptModuleSpec;

/**
 * Tests for {@link CassandraArchiveRepository}
 * @author Vasanth Asokan
 */
public class CassandraArchiveRepositoryTest {

    /**
     * Metadata for test resources found in test/resource
     */
    public static enum TestResource {
        TEST_HELLOWORLD_JAR("helloworld", "testmodules/helloworld.jar");

        private final ModuleId moduleId;
        private final String resourcePath;
        private TestResource(String moduleId, String resourcePath) {
            this.moduleId = ModuleId.create(moduleId);
            this.resourcePath = resourcePath;
        }
        /**
         * @return the expected moduleId after this is converted to a archive
         */
        public ModuleId getModuleId() {
            return moduleId;
        }
        /**
         * @return path name suitable for passing to {@link ClassLoader#getResource(String)}
         */
        public String getResourcePath() {
            return resourcePath;
        }
    }

    private CassandraArchiveRepository repository;
    private Path testArchiveJarFile;
    private CassandraGateway gateway;
    private CassandraArchiveRepositoryConfig config;

    @BeforeClass
    public void setup() throws Exception {
        gateway = mock(CassandraGateway.class);
        Keyspace mockKeyspace = mock(Keyspace.class);
        when(mockKeyspace.getKeyspaceName()).thenReturn("testKeySpace");

        when(gateway.getKeyspace()).thenReturn(mockKeyspace);
        when(gateway.getColumnFamily()).thenReturn("testColumnFamily");

        config = new BasicCassandraRepositoryConfig.Builder(gateway)
            .setRepositoryId("TestRepo")
            .setArchiveOutputDirectory(Files.createTempDirectory(this.getClass().getSimpleName() + "_"))
            .build();
        repository = new CassandraArchiveRepository(config);


        URL testJarUrl = getClass().getClassLoader().getResource(TestResource.TEST_HELLOWORLD_JAR.getResourcePath());
        if (testJarUrl == null) {
            fail("Couldn't locate " + TestResource.TEST_HELLOWORLD_JAR.getResourcePath());
        }
        testArchiveJarFile = Files.createTempFile(TestResource.TEST_HELLOWORLD_JAR.getModuleId().toString(), ".jar");
        InputStream inputStream = testJarUrl.openStream();
        Files.copy(inputStream, testArchiveJarFile, StandardCopyOption.REPLACE_EXISTING);
        IOUtils.closeQuietly(inputStream);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testInsertArchive() throws IOException {
        JarScriptArchive jarArchive = new JarScriptArchive.Builder(testArchiveJarFile).build();
        repository.insertArchive(jarArchive);

        Map<String, Object> columns = new HashMap<String, Object>();
        Path jarFilePath;
        try {
            jarFilePath = Paths.get(jarArchive.getRootUrl().toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        ScriptModuleSpec moduleSpec = jarArchive.getModuleSpec();
        String serialized = config.getModuleSpecSerializer().serialize(moduleSpec);
        byte[] jarBytes = Files.readAllBytes(jarFilePath);
        columns.put(Columns.shard_num.name(), repository.calculateShardNum(moduleSpec.getModuleId()));
        columns.put(Columns.last_update.name(), jarArchive.getCreateTime());
        columns.put(Columns.archive_content.name(), jarBytes);
        columns.put(Columns.archive_content_hash.name(), repository.calculateHash(jarBytes));
        columns.put(Columns.module_spec.name(), serialized);

        ArgumentCaptor<String> argument1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> argument2 = ArgumentCaptor.forClass(Map.class);
        verify(gateway).upsert(argument1.capture(),
                argument2.capture());

        assertEquals(moduleSpec.getModuleId().toString(), argument1.getValue());
        Map columnMap = argument2.getValue();
        assertEquals(repository.calculateShardNum(moduleSpec.getModuleId()), columnMap.get(Columns.shard_num.name()));
        assertTrue(Arrays.equals(jarBytes, (byte[])columnMap.get(Columns.archive_content.name())));
        assertTrue(Arrays.equals(repository.calculateHash(jarBytes), (byte[])columnMap.get(Columns.archive_content_hash.name())));
        assertEquals(serialized, (String)columnMap.get(Columns.module_spec.name()));
        assertEquals(jarArchive.getCreateTime(), (long)columnMap.get(Columns.last_update.name()));
    }

    @Test(expectedExceptions=UnsupportedOperationException.class)
    public void testArchiveWithDeploySpecs() throws IOException {
        JarScriptArchive jarArchive = new JarScriptArchive.Builder(testArchiveJarFile).build();
        repository.insertArchive(jarArchive, null);
    }

    @Test(expectedExceptions=UnsupportedOperationException.class)
    public void testAddDeploySpecs() throws IOException {
        repository.putDeploySpecs(ModuleId.create("testModuleId"), Collections.<String, Object>emptyMap());
    }

    @Test(expectedExceptions=UnsupportedOperationException.class)
    public void testGetView() throws IOException {
        repository.getView("");
    }

    @Test
    public void testDeleteArchive() throws IllegalArgumentException, IOException {
        repository.deleteArchive(ModuleId.fromString("testModule.v3"));
        verify(gateway).deleteRow("testModule.v3");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetRows() throws Exception {
        EnumSet<Columns> columns = EnumSet.of(Columns.module_id, Columns.module_name);
        Rows<String, String> mockRows = mock(Rows.class);
        Row<String, String> row1 = mock(Row.class);
        Row<String, String> row2 = mock(Row.class);
        List<Row<String, String>> rowList = Arrays.asList(row1, row2);

        when(mockRows.iterator()).thenReturn(rowList.iterator());

        FutureTask<Rows<String, String>> future = new FutureTask<Rows<String, String>>(new Runnable() {
            @Override
            public void run() {
            }
        }, mockRows);
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.execute(future);
        when(gateway.selectAsync(anyString())).thenReturn(future);

        repository.getRows(columns);
        List<String> selectList = new ArrayList<String>();
        for (int shardNum = 0; shardNum < config.getShardCount(); shardNum++) {
            selectList.add(repository.generateSelectByShardCql(columns, shardNum));
        }

        InOrder inOrder = inOrder(gateway);
        for (int shardNum = 0; shardNum < config.getShardCount(); shardNum++) {
            inOrder.verify(gateway).selectAsync(selectList.get(shardNum));
        }
    }

    @Test
    public void testGetArchiveUpdateTimes() {
        // TODO: Fill out test.
    }

    @Test
    public void testGetArchiveSummaries() {
        // TODO: Fill out test.
    }


    @Test
    public void testGetRepositorySummary() throws Exception {
        // TODO: Fill out test.
    }

    @Test
    public void testGetScriptArchives() {
        // TODO: Fill out test.
    }

}
