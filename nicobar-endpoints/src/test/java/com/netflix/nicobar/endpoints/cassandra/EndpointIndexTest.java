package com.netflix.nicobar.endpoints.cassandra;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.nicobar.cassandra.internal.CassandraGateway;
import com.netflix.nicobar.endpoints.EndpointIndex;
import com.netflix.nicobar.endpoints.EndpointSummary;
import com.netflix.nicobar.endpoints.EndpointSummarySerializer;

/**
 * Tests for {@link CassandraEndpointIndex}.
 *
 * @author Vasanth Asokan
 */
public class EndpointIndexTest {
    private EndpointIndex index;
    private static final String rowKey = "indexRow";

    @Mock
    private CassandraGateway gateway;
    @Mock
    private EndpointSummarySerializer serializer;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.initMocks(this);
        index = new CassandraEndpointIndex(rowKey, gateway, serializer);
    }

    @Test
    public void testSet() throws IOException {
        EndpointSummary summary = new EndpointSummary(null, "v1", System.currentTimeMillis());
        when(serializer.serialize(any(EndpointSummary.class))).thenReturn("Serialized");
        index.set("/test/path", summary);

        verify(gateway).upsert(rowKey, Collections.singletonMap("/test/path", (Object)"Serialized"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGet() throws IOException {
        String endpointUri = "/test/path";
        EndpointSummary summary = new EndpointSummary("foo", "v1", System.currentTimeMillis());

        when(serializer.deserialize("Serialized")).thenReturn(summary);
        Column<String> mockColumn = mock(Column.class);
        when(mockColumn.getStringValue()).thenReturn("Serialized");
        ColumnList<String> mockColumns = mock(ColumnList.class);
        when(mockColumns.size()).thenReturn(1);
        when(mockColumns.getColumnByName(endpointUri)).thenReturn(mockColumn);
        when(gateway.getColumns(rowKey, endpointUri)).thenReturn(mockColumns);

        assertEquals(summary, index.get(endpointUri));
        verify(gateway).getColumns(rowKey, endpointUri);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetReturnsNull() throws IOException {
        String endpointUri = "/test/path";

        ColumnList<String> mockColumns = mock(ColumnList.class);
        when(mockColumns.size()).thenReturn(0);
        when(gateway.getColumns(rowKey, endpointUri)).thenReturn(mockColumns);

        assertNull(index.get(endpointUri));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetReturnsNull2() throws IOException {
        String endpointUri = "/test/path";

        ColumnList<String> mockColumns = mock(ColumnList.class);
        when(mockColumns.size()).thenReturn(1);
        when(mockColumns.getColumnByName(endpointUri)).thenReturn(null);
        when(gateway.getColumns(rowKey, endpointUri)).thenReturn(mockColumns);

        assertNull(index.get(endpointUri));
    }

    @Test
    public void testRemove() throws IOException {
        String endpointUri = "/test/path";
        index.remove(endpointUri);
        verify(gateway).deleteColumn(rowKey, endpointUri);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetSummaryMap() throws IOException {
        String endpointUri1 = "/test/path";
        EndpointSummary summary1 = new EndpointSummary("foo", "v1", 1001L);

        String endpointUri2 = "/test/path2";
        EndpointSummary summary2 = new EndpointSummary("bar", "v2", 2001L);

        when(serializer.deserialize("Serialized1")).thenReturn(summary1);
        when(serializer.deserialize("Serialized2")).thenReturn(summary2);

        Column<String> mockColumn1 = mock(Column.class);
        when(mockColumn1.getName()).thenReturn(endpointUri1);
        when(mockColumn1.getStringValue()).thenReturn("Serialized1");
        Column<String> mockColumn2 = mock(Column.class);
        when(mockColumn2.getName()).thenReturn(endpointUri2);
        when(mockColumn2.getStringValue()).thenReturn("Serialized2");
        Iterator<Column<String>> columnListIterator = Arrays.asList(mockColumn1, mockColumn2).iterator();

        ColumnList<String> mockColumns = mock(ColumnList.class);
        when(mockColumns.iterator()).thenReturn(columnListIterator);
        when(gateway.getRow(rowKey)).thenReturn(mockColumns);

        Map<String, EndpointSummary> expectedMap = new HashMap<String, EndpointSummary>();
        expectedMap.put(endpointUri1, summary1);
        expectedMap.put(endpointUri2, summary2);

        Map<String, EndpointSummary> actualMap = index.getSummaryMap();

        assertEquals(expectedMap, actualMap);

    }
}