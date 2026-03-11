package com.simhospital.message;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.datatype.CX;
import ca.uhn.hl7v2.model.v25.datatype.XPN;
import ca.uhn.hl7v2.model.v25.group.ORU_R01_ORDER_OBSERVATION;
import ca.uhn.hl7v2.model.v25.message.*;
import ca.uhn.hl7v2.model.v25.segment.MSH;
import ca.uhn.hl7v2.model.v25.segment.PID;
import ca.uhn.hl7v2.model.v25.segment.PV1;
import com.simhospital.patient.OrderDetails;
import com.simhospital.patient.PatientState;
import com.simhospital.pathway.PathwayEvent;
import com.simhospital.pathway.PathwayEventType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class MessageFactory {

    private static final DateTimeFormatter HL7_DATETIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneId.of("UTC"));
    private static final DateTimeFormatter HL7_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneId.of("UTC"));

    private record TestPanel(String[] names, String[] units, double[][] ranges) {
        String name(int i) { return names[i]; }
        String unit(int i) { return units[i]; }
        double low(int i)  { return ranges[i][0]; }
        double high(int i) { return ranges[i][1]; }
        int size()         { return names.length; }
    }

    private static final TestPanel LAB_PANEL = new TestPanel(
            new String[]{"Hemoglobin", "White Blood Cell Count", "Platelet Count", "Sodium",
                          "Potassium", "Creatinine", "Glucose", "Calcium", "ALT", "AST"},
            new String[]{"g/dL", "x10^9/L", "x10^9/L", "mmol/L",
                          "mmol/L", "mg/dL", "mg/dL", "mg/dL", "U/L", "U/L"},
            new double[][]{{12.0, 17.5}, {4.0, 11.0}, {150.0, 400.0}, {136.0, 145.0},
                           {3.5, 5.1}, {0.6, 1.2}, {70.0, 100.0}, {8.5, 10.5}, {7.0, 56.0}, {10.0, 40.0}}
    );

    private static final TestPanel VITAL_PANEL = new TestPanel(
            new String[]{"Heart Rate", "Systolic BP", "Diastolic BP", "Temperature", "Respiratory Rate", "SpO2"},
            new String[]{"bpm", "mmHg", "mmHg", "degC", "breaths/min", "%"},
            new double[][]{{60.0, 100.0}, {90.0, 140.0}, {60.0, 90.0}, {36.1, 37.2}, {12.0, 20.0}, {95.0, 100.0}}
    );

    private static final Map<PathwayEventType, String> TRIGGER_EVENTS = Map.ofEntries(
            Map.entry(PathwayEventType.REGISTRATION, "A04"),
            Map.entry(PathwayEventType.PRE_ADMISSION, "A05"),
            Map.entry(PathwayEventType.TRANSFER_IN_ERROR, "A02"),
            Map.entry(PathwayEventType.DISCHARGE_IN_ERROR, "A03"),
            Map.entry(PathwayEventType.CANCEL_VISIT, "A11"),
            Map.entry(PathwayEventType.CANCEL_TRANSFER, "A12"),
            Map.entry(PathwayEventType.CANCEL_DISCHARGE, "A13"),
            Map.entry(PathwayEventType.ADD_PERSON, "A28"),
            Map.entry(PathwayEventType.UPDATE_PERSON, "A31"),
            Map.entry(PathwayEventType.PENDING_ADMISSION, "A14"),
            Map.entry(PathwayEventType.PENDING_DISCHARGE, "A16"),
            Map.entry(PathwayEventType.PENDING_TRANSFER, "A15"),
            Map.entry(PathwayEventType.CANCEL_PENDING_ADMISSION, "A27"),
            Map.entry(PathwayEventType.CANCEL_PENDING_DISCHARGE, "A25"),
            Map.entry(PathwayEventType.CANCEL_PENDING_TRANSFER, "A26"),
            Map.entry(PathwayEventType.DELETE_VISIT, "A23"),
            Map.entry(PathwayEventType.TRACK_DEPARTURE, "A09"),
            Map.entry(PathwayEventType.TRACK_ARRIVAL, "A10"),
            Map.entry(PathwayEventType.MERGE, "A34"),
            Map.entry(PathwayEventType.BED_SWAP, "A17"),
            Map.entry(PathwayEventType.CLINICAL_NOTE, "R01"),
            Map.entry(PathwayEventType.DOCUMENT, "R01")
    );

    private final Random random;

    public MessageFactory(Random random) {
        this.random = random;
    }

    /**
     * Builds a HAPI Message from the given event and patient state.
     * Returns null for DELAY events (no message produced).
     */
    public Message build(PathwayEvent event, PatientState patient, Instant eventTime) {
        if (event.type() == PathwayEventType.DELAY) return null;

        try {
            return buildMessage(event, patient, eventTime);
        } catch (HL7Exception e) {
            throw new RuntimeException("Failed to build HL7 message for " + event.type(), e);
        }
    }

    private Message buildMessage(PathwayEvent event, PatientState patient, Instant eventTime) throws HL7Exception {
        return switch (event.type()) {
            case ADMISSION   -> buildAdt(new ADT_A01(), "A01", patient, eventTime);
            case DISCHARGE   -> buildAdt(new ADT_A03(), "A03", patient, eventTime);
            case TRANSFER    -> buildAdt(new ADT_A02(), "A02", patient, eventTime);
            case ORDER       -> buildOrder(patient, eventTime, event, "NW");
            case CANCEL_ORDER -> buildOrder(patient, eventTime, event, "CA");
            case RESULT      -> buildResult(patient, eventTime, event, LAB_PANEL);
            case OBSERVATION -> buildResult(patient, eventTime, event, VITAL_PANEL);
            default          -> buildGenericAdt(patient, eventTime, event.type());
        };
    }

    private ADT_A01 buildAdt(ADT_A01 msg, String trigger, PatientState patient, Instant eventTime) throws HL7Exception {
        populateMSH(msg.getMSH(), "ADT", trigger, eventTime);
        populatePID(msg.getPID(), patient);
        populatePV1(msg.getPV1(), patient, eventTime);
        return msg;
    }

    private ADT_A02 buildAdt(ADT_A02 msg, String trigger, PatientState patient, Instant eventTime) throws HL7Exception {
        populateMSH(msg.getMSH(), "ADT", trigger, eventTime);
        populatePID(msg.getPID(), patient);
        populatePV1(msg.getPV1(), patient, eventTime);
        return msg;
    }

    private ADT_A03 buildAdt(ADT_A03 msg, String trigger, PatientState patient, Instant eventTime) throws HL7Exception {
        populateMSH(msg.getMSH(), "ADT", trigger, eventTime);
        populatePID(msg.getPID(), patient);
        populatePV1(msg.getPV1(), patient, eventTime);
        return msg;
    }

    private ORM_O01 buildOrder(PatientState patient, Instant eventTime,
                                PathwayEvent event, String orderControl) throws HL7Exception {
        ORM_O01 msg = new ORM_O01();
        populateMSH(msg.getMSH(), "ORM", "O01", eventTime);
        populatePID(msg.getPATIENT().getPID(), patient);
        populatePV1(msg.getPATIENT().getPATIENT_VISIT().getPV1(), patient, eventTime);

        String orderId = requireParam(event.parameters(), "order_id", "ORDER");
        String orderProfile = requireParam(event.parameters(), "order_profile", "ORDER");

        var orc = msg.getORDER().getORC();
        orc.getOrderControl().setValue(orderControl);
        orc.getPlacerOrderNumber().getEntityIdentifier().setValue(orderId);

        var obr = msg.getORDER().getORDER_DETAIL().getOBR();
        obr.getSetIDOBR().setValue("1");
        obr.getUniversalServiceIdentifier().getIdentifier().setValue(orderProfile);
        obr.getUniversalServiceIdentifier().getText().setValue(orderProfile);

        trackOrder(patient, orderId, orderProfile, orderControl);
        return msg;
    }

    private void trackOrder(PatientState patient, String orderId, String orderProfile, String orderControl) {
        if ("NW".equals(orderControl)) {
            patient.getOpenOrders().put(orderId, new OrderDetails(orderProfile, orderProfile));
        } else if ("CA".equals(orderControl)) {
            patient.getOpenOrders().remove(orderId);
        }
    }

    private ORU_R01 buildResult(PatientState patient, Instant eventTime,
                                 PathwayEvent event, TestPanel panel) throws HL7Exception {
        ORU_R01 msg = new ORU_R01();
        populateMSH(msg.getMSH(), "ORU", "R01", eventTime);

        var patientResult = msg.getPATIENT_RESULT();
        populatePID(patientResult.getPATIENT().getPID(), patient);
        populatePV1(patientResult.getPATIENT().getVISIT().getPV1(), patient, eventTime);

        String orderProfile = event.parameters().getOrDefault("order_profile", panel.name(0));
        var obr = patientResult.getORDER_OBSERVATION().getOBR();
        obr.getSetIDOBR().setValue("1");
        obr.getUniversalServiceIdentifier().getIdentifier().setValue(orderProfile);
        obr.getUniversalServiceIdentifier().getText().setValue(orderProfile);

        populateObservations(patientResult.getORDER_OBSERVATION(), panel);
        return msg;
    }

    private void populateObservations(ORU_R01_ORDER_OBSERVATION orderObs, TestPanel panel) throws HL7Exception {
        int numResults = 1 + random.nextInt(3);
        for (int i = 0; i < numResults; i++) {
            int testIdx = random.nextInt(panel.size());
            var obx = orderObs.getOBSERVATION(i).getOBX();

            obx.getSetIDOBX().setValue(String.valueOf(i + 1));
            obx.getValueType().setValue("NM");
            obx.getObservationIdentifier().getIdentifier().setValue(panel.name(testIdx));
            obx.getObservationIdentifier().getText().setValue(panel.name(testIdx));
            obx.getUnits().getIdentifier().setValue(panel.unit(testIdx));

            double value = panel.low(testIdx) + random.nextDouble() * (panel.high(testIdx) - panel.low(testIdx));
            obx.getObservationValue(0).parse(String.format("%.1f", value));
            obx.getReferencesRange().setValue(String.format("%.1f-%.1f", panel.low(testIdx), panel.high(testIdx)));
            obx.getObservationResultStatus().setValue("F");
        }
    }

    private Message buildGenericAdt(PatientState patient, Instant eventTime,
                                     PathwayEventType type) throws HL7Exception {
        ADT_A01 msg = new ADT_A01();
        String triggerEvent = TRIGGER_EVENTS.getOrDefault(type, "A01");
        populateMSH(msg.getMSH(), "ADT", triggerEvent, eventTime);
        populatePID(msg.getPID(), patient);
        populatePV1(msg.getPV1(), patient, eventTime);
        return msg;
    }

    private void populateMSH(MSH msh, String messageType, String triggerEvent,
                              Instant eventTime) throws HL7Exception {
        msh.getFieldSeparator().setValue("|");
        msh.getEncodingCharacters().setValue("^~\\&");
        msh.getSendingApplication().getNamespaceID().setValue("SimHospital");
        msh.getSendingFacility().getNamespaceID().setValue("SimHospital-Facility");
        msh.getDateTimeOfMessage().getTime().setValue(HL7_DATETIME.format(eventTime));
        msh.getMessageType().getMessageCode().setValue(messageType);
        msh.getMessageType().getTriggerEvent().setValue(triggerEvent);
        msh.getMessageControlID().setValue(UUID.randomUUID().toString());
        msh.getProcessingID().getProcessingID().setValue("P");
        msh.getVersionID().getVersionID().setValue("2.5");
    }

    private void populatePID(PID pid, PatientState patient) throws HL7Exception {
        CX cx = pid.getPatientIdentifierList(0);
        cx.getIDNumber().setValue(patient.getMrn());
        cx.getAssigningAuthority().getNamespaceID().setValue("SimHospital");
        cx.getIdentifierTypeCode().setValue("MR");

        XPN name = pid.getPatientName(0);
        name.getFamilyName().getSurname().setValue(patient.getFamilyName());
        name.getGivenName().setValue(patient.getGivenName());

        if (patient.getDateOfBirth() != null) {
            pid.getDateTimeOfBirth().getTime().setValue(
                    HL7_DATE.format(patient.getDateOfBirth().atStartOfDay(ZoneId.of("UTC"))));
        }

        pid.getAdministrativeSex().setValue(patient.getGender());
    }

    private void populatePV1(PV1 pv1, PatientState patient, Instant eventTime) throws HL7Exception {
        pv1.getSetIDPV1().setValue("1");
        pv1.getPatientClass().setValue("I");

        if (patient.getWard() != null) {
            pv1.getAssignedPatientLocation().getPointOfCare().setValue(patient.getWard());
        }
        if (patient.getBed() != null) {
            pv1.getAssignedPatientLocation().getBed().setValue(patient.getBed());
        }

        if (patient.getAdmissionTime() != null) {
            pv1.getAdmitDateTime().getTime().setValue(
                    HL7_DATETIME.format(patient.getAdmissionTime()));
        }
    }

    private static String requireParam(Map<String, String> params, String key, String context) {
        String value = params.get(key);
        if (value != null) return value;
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
