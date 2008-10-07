/*
 * Copyright 2006-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.batch.sample;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.LineTokenizer;
import org.springframework.batch.sample.domain.trade.Trade;
import org.springframework.batch.sample.domain.trade.internal.TradeFieldSetMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration()
public class FixedLengthImportJobFunctionalTests extends AbstractValidatingBatchLauncherTests {

	//expected line length in input file (sum of pattern lengths + 2, because the counter is appended twice)
	private static final int LINE_LENGTH = 29;

	//auto-injected attributes
	private SimpleJdbcTemplate simpleJdbcTemplate;
	private Resource fileLocator;
	private FlatFileItemReader<Trade> inputSource;
	private LineTokenizer lineTokenizer;

	@Autowired
	public void setDataSource(DataSource dataSource) {
		this.simpleJdbcTemplate = new SimpleJdbcTemplate(dataSource);
	}

	@Autowired
	public void setLineTokenizer(LineTokenizer lineTokenizer) {
		this.lineTokenizer = lineTokenizer;
	}


	@Before
	public void onSetUp() throws Exception {
		simpleJdbcTemplate.update("delete from TRADE");
		fileLocator = new ClassPathResource("data/fixedLengthImportJob/input/20070122.teststream.ImportTradeDataStep.txt");
		inputSource = new FlatFileItemReader<Trade>();
		
		FieldSetMapper<Trade> mapper = new TradeFieldSetMapper();
		DefaultLineMapper<Trade> lineMapper = new DefaultLineMapper<Trade>();
		lineMapper.setLineTokenizer(lineTokenizer);
		lineMapper.setFieldSetMapper(mapper);
		inputSource.setLineMapper(lineMapper);
		
		
		inputSource.setResource(fileLocator);
	}

	/**
	 * Check that records have been correctly written to database
	 * @throws Exception 
	 */
	protected void validatePostConditions() throws Exception {
		
		inputSource.open(new ExecutionContext());

		simpleJdbcTemplate.getJdbcOperations().query(
				"SELECT ID, ISIN, QUANTITY, PRICE, CUSTOMER FROM trade ORDER BY id", 
				new RowCallbackHandler() {
					public void processRow(ResultSet rs) throws SQLException {
						Trade trade;
						try {
							trade = inputSource.read();
						}
						catch (Exception e) {
							throw new IllegalStateException(e.getMessage());
						}
						assertEquals(trade.getIsin(), rs.getString(2));
						assertEquals(trade.getQuantity(),rs.getLong(3));
						assertEquals(trade.getPrice(), rs.getBigDecimal(4));
						assertEquals(trade.getCustomer(), rs.getString(5));
					}

				});

		assertNull(inputSource.read());
	}

	/*
	 * fixed-length file is expected on input
	 */
	protected void validatePreConditions() throws Exception{
		BufferedReader reader;

		reader = new BufferedReader(new FileReader(fileLocator.getFile()));
		String line;
		while ((line = reader.readLine()) != null) {
			assertEquals (LINE_LENGTH, line.length());
		}
	}

}
