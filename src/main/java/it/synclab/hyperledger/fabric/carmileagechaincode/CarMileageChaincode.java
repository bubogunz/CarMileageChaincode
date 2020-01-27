package it.synclab.hyperledger.fabric.carmileagechaincode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperledger.fabric.protos.common.Common.Status;
//import org.hyperledger.fabric.protos.peer.Chaincode;
import org.hyperledger.fabric.shim.Chaincode;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CarMileageChaincode implements Chaincode {

	private static Log LOG = LogFactory.getLog(CarMileageChaincode.class);

	public static final String INVOKE_FUNCTION = "invoke";
	public static final String QUERY_FUNCTION = "query";
	public static final String QUERY_HISTORY_FUNCTION = "queryHistory";
	
	public static void main(String[] args) {
		LOG.info("main was invoked with parameters:");
		IntStream.range(0,args.length).forEach(
			idx -> LOG.info("args[" + idx + "] = " + args[idx])
		);
	}

	@Override
	public Response init(ChaincodeStub chaincodeStub) {
		LOG.info("Init requested");
		return new Chaincode.Response(Status.SUCCESS_VALUE, "Init requested", "init requested".getBytes());
	}

	@Override
	public Response invoke(ChaincodeStub chaincodeStub) {
		String functionName = chaincodeStub.getFunction();
		LOG.info("function name: " + functionName);

		List<String> paramList = chaincodeStub.getParameters();
		IntStream.range(0, paramList.size()).forEach(
				idx -> LOG.info("value of param: " + idx + " is: " + paramList.get(idx)
						));

		if (INVOKE_FUNCTION.equalsIgnoreCase(functionName)) {
			return performInvokeOperation(chaincodeStub, paramList);
		} else if (QUERY_FUNCTION.equalsIgnoreCase(functionName)) {
			return performQueryOperation(chaincodeStub, paramList);
		} else if (QUERY_HISTORY_FUNCTION.equalsIgnoreCase(functionName)) {
			return performQueryByHistoryFunction(chaincodeStub, paramList);
		} else
			return new Chaincode.Response(Status.BAD_REQUEST_VALUE, functionName + " function is not supported yet", (functionName + " function is not supported yet").getBytes());
	}

	private Response performQueryByHistoryFunction(ChaincodeStub chaincodeStub, List<String> paramList) {
		if (listHasDifferentSizeThen(paramList, 1)) {
			return new Chaincode.Response(Status.BAD_REQUEST_VALUE, "Incorrect number of arguments, " + paramList.size() + " provided, 1 wanted", ("Incorrect number of arguments, " + paramList.size() + " provided, 1 wanted").getBytes());
		}
		QueryResultsIterator<KeyModification> queryResultsIterator = chaincodeStub.getHistoryForKey(paramList.get(0));
		return new Chaincode.Response(Status.SUCCESS_VALUE, buildJsonFromQueryResult(queryResultsIterator), buildJsonFromQueryResult(queryResultsIterator).getBytes());

	}

	private String buildJsonFromQueryResult(QueryResultsIterator<KeyModification> queryResultsIterator) {

		JSONArray jsonArray = new JSONArray();
		queryResultsIterator.forEach(keyModification -> {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("transactionId", keyModification.getTxId());
			map.put("timestamp", keyModification.getTimestamp().toString());
			map.put("value", keyModification.getStringValue());
			map.put("isDeleted", keyModification.isDeleted());
			jsonArray.put(map);
		});

		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.accumulate("transactions", jsonArray);
		} catch (JSONException e) {
			throw new RuntimeException("exception while generating json object");
		}
		return jsonObject.toString();
	}

	private Response performQueryOperation(ChaincodeStub chaincodeStub, List<String> paramList) {
		if (listHasDifferentSizeThen(paramList, 1)) {
			return new Chaincode.Response(Status.BAD_REQUEST_VALUE, "Incorrect number of arguments, " + paramList.size() + " provided, 1 wanted", ("Incorrect number of arguments, " + paramList.size() + " provided, 1 wanted").getBytes());
		}
		String carMileage = chaincodeStub.getStringState(paramList.get(0));
		if (Objects.isNull(carMileage)) {
			return new Chaincode.Response(Status.BAD_REQUEST_VALUE, "Mileage of provided car " + carMileage + " not found", ("Mileage of provided car " + carMileage + " not found").getBytes());
		}
		return new Chaincode.Response(Status.SUCCESS_VALUE, carMileage, carMileage.getBytes());
	}

	private Response performInvokeOperation(ChaincodeStub chaincodeStub, List<String> paramList) {
		if (listHasDifferentSizeThen(paramList, 2)) {
			return new Chaincode.Response(Status.BAD_REQUEST_VALUE, "Incorrect number of arguments, " + paramList.size() + " provided, 2 wanted", ("Incorrect number of arguments, " + paramList.size() + " provided, 2 wanted").getBytes());
		}
		String carId = paramList.get(0);
		String carMileageToUpdate = paramList.get(1);

		if (!StringUtils.isNumeric(carMileageToUpdate)) {
			return new Chaincode.Response(Status.BAD_REQUEST_VALUE, "Incorrect mileage provided: value is not numeric!", ("Incorrect mileage provided: value is not numeric!").getBytes());
		}

		String carMileageFromLedger = chaincodeStub.getStringState(carId);

		if (StringUtils.isEmpty(carMileageFromLedger)) {
			chaincodeStub.putStringState(carId, carMileageToUpdate);
		} else {
			if (Integer.valueOf(carMileageFromLedger).compareTo(Integer.valueOf(carMileageToUpdate)) >= 0) {
				return new Chaincode.Response(Status.BAD_REQUEST_VALUE, "Incorrect value", "Incorrect value".getBytes());
			}
			chaincodeStub.putStringState(carId, carMileageToUpdate);
		}
		return new Chaincode.Response(Status.SUCCESS_VALUE, "", chaincodeStub.getBinding());
	} 	

	private boolean listHasDifferentSizeThen(List<String> list, int expectedElementsNumber) {
		return list.size() != expectedElementsNumber;
	}
}
