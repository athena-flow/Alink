package com.alibaba.alink.operator.batch.recommendation;

import org.apache.flink.types.Row;

import com.alibaba.alink.common.MTableUtil;
import com.alibaba.alink.common.utils.JsonConverter;
import com.alibaba.alink.operator.batch.BatchOperator;
import com.alibaba.alink.operator.batch.dataproc.ToMTableBatchOp;
import com.alibaba.alink.operator.batch.source.MemSourceBatchOp;
import com.alibaba.alink.pipeline.Pipeline;
import com.alibaba.alink.pipeline.classification.LogisticRegression;
import com.alibaba.alink.pipeline.dataproc.JsonValue;
import com.alibaba.alink.pipeline.dataproc.vector.VectorAssembler;
import com.alibaba.alink.pipeline.feature.MultiHotEncoder;
import com.alibaba.alink.pipeline.feature.OneHotEncoder;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class RecommendationRankingBatchOpTest {
	@Test
	public void test() throws Exception {

		Row[] predArray = new Row[] {
			Row.of("u6", "0.0 1.0", 0.0, 1.0, 1, "{\"data\":{\"iid\":[18,19,88]},"
				+ "\"schema\":\"iid INT\"}")
		};

		Row[] trainArray = new Row[] {
			Row.of("u0", "1.0 1.0", 1.0, 1.0, 1, 18),
			Row.of("u1", "1.0 1.0", 1.0, 1.0, 0, 19),
			Row.of("u2", "1.0 0.0", 1.0, 0.0, 1, 88),
			Row.of("u3", "1.0 0.0", 1.0, 0.0, 1, 18),
			Row.of("u4", "0.0 1.0", 0.0, 1.0, 1, 88),
			Row.of("u5", "0.0 1.0", 0.0, 1.0, 1, 19),
			Row.of("u6", "0.0 1.0", 0.0, 1.0, 1, 88)
		};
		BatchOperator <?> trainData = new MemSourceBatchOp(Arrays.asList(trainArray),
			new String[] {"uid", "uf", "f0", "f1", "labels", "iid"});
		BatchOperator <?> predData =  new MemSourceBatchOp(Arrays.asList(predArray),
			new String[] {"uid", "uf", "f0", "f1", "labels", "ilist"})
			.link(new ToMTableBatchOp().setSelectedCol("ilist"));

		String[] oneHotCols = new String[] {"uid", "f0", "f1", "iid"};
		String[] multiHotCols = new String[] {"uf"};

		Pipeline pipe = new Pipeline()
			.add(
				new OneHotEncoder()
					.setSelectedCols(oneHotCols)
					.setOutputCols("ovec"))
			.add(
				new MultiHotEncoder().setDelimiter(" ")
					.setSelectedCols(multiHotCols)
					.setOutputCols("mvec"))
			.add(
				new VectorAssembler()
					.setSelectedCols("ovec", "mvec")
					.setOutputCol("vec"))
			.add(
				new LogisticRegression()
					.setVectorCol("vec")
					.setLabelCol("labels")
					.setReservedCols("uid", "iid")
					.setPredictionDetailCol("detail")
					.setPredictionCol("pred"))
			.add(
				new JsonValue()
					.setSelectedCol("detail")
					.setJsonPath("$.1")
					.setOutputCols("score"));
		RecommendationRankingBatchOp rank = new RecommendationRankingBatchOp()
			.setMTableCol("ilist")
			.setOutputCol("ilist")
			.setTopN(2)
			.setRankingCol("score")
			.setReservedCols("uid", "labels");
		BatchOperator<?> result = rank.linkFrom(pipe.fit(trainData).save(), predData);
		List <Row> rows = result.collect();
		Assert.assertEquals(JsonConverter.toJson(rows.get(0).getField(2)),
			"{\"data\":{\"iid\":[18,88],\"score\":[0.9999999999999553,0"
			+ ".9999999999999472]},\"schema\":\"iid INT,score DOUBLE\"}");
	}
}