package com.grash.service;

import com.grash.dto.WorkOrderMeterTriggerPatchDTO;
import com.grash.dto.WorkOrderMeterTriggerPostDTO;
import com.grash.dto.cutomField.CustomFieldValuePostDTO;
import com.grash.dto.license.LicenseEntitlement;
import com.grash.exception.CustomException;
import com.grash.mapper.WorkOrderMeterTriggerMapper;
import com.grash.model.*;
import com.grash.model.enums.CustomFieldEntityType;
import com.grash.model.enums.WorkOrderMeterTriggerCondition;
import com.grash.repository.ReadingRepository;
import com.grash.repository.WorkOrderMeterTriggerRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkOrderMeterTriggerService {
    private final WorkOrderMeterTriggerRepository workOrderMeterTriggerRepository;
    private final ReadingRepository readingRepository;
    private final WorkOrderService workOrderService;
    private final WorkOrderMeterTriggerMapper workOrderMeterTriggerMapper;
    private final MeterService meterService;
    private final EntityManager em;
    private final LicenseService licenseService;
    private final CustomFieldValueService customFieldValueService;

    @Transactional
    public WorkOrderMeterTrigger create(WorkOrderMeterTrigger workOrderMeterTrigger, Company company) {
        if (!licenseService.hasEntitlement(LicenseEntitlement.CONDITION_BASED_PM))
            throw new CustomException("You need a license to create a meter trigger", HttpStatus.FORBIDDEN);

        if (workOrderMeterTrigger instanceof WorkOrderMeterTriggerPostDTO workOrderMeterTriggerPostDTO) {
            workOrderMeterTrigger = workOrderMeterTriggerMapper.fromPostDto(workOrderMeterTriggerPostDTO);
            if (!workOrderMeterTriggerPostDTO.getCustomFields().isEmpty()) {
                setMeterTriggerCustomFields(workOrderMeterTrigger, workOrderMeterTriggerPostDTO.getCustomFields(),
                        company);
            }
        }
        WorkOrderMeterTrigger savedWorkOrderMeterTrigger =
                workOrderMeterTriggerRepository.saveAndFlush(workOrderMeterTrigger);
        em.refresh(savedWorkOrderMeterTrigger);

        if (savedWorkOrderMeterTrigger.getTriggerCondition() == WorkOrderMeterTriggerCondition.EVERY) {
            savedWorkOrderMeterTrigger.setLastTriggeredValue(getLatestMeterReadingValue(savedWorkOrderMeterTrigger.getMeter()));
            savedWorkOrderMeterTrigger = workOrderMeterTriggerRepository.saveAndFlush(savedWorkOrderMeterTrigger);
        }

        return savedWorkOrderMeterTrigger;
    }

    @Transactional
    public WorkOrderMeterTrigger update(Long id, WorkOrderMeterTriggerPatchDTO workOrderMeterTrigger, Company company) {
        if (workOrderMeterTriggerRepository.existsById(id)) {
            WorkOrderMeterTrigger savedWorkOrderMeterTrigger = workOrderMeterTriggerRepository.findById(id).get();
            WorkOrderMeterTriggerCondition previousCondition = savedWorkOrderMeterTrigger.getTriggerCondition();

            if (!workOrderMeterTrigger.getCustomFields().isEmpty()) {
                setMeterTriggerCustomFields(savedWorkOrderMeterTrigger, workOrderMeterTrigger.getCustomFields(),
                        company);
            }
            WorkOrderMeterTrigger updated = workOrderMeterTriggerMapper.updateWorkOrderMeterTrigger(savedWorkOrderMeterTrigger, workOrderMeterTrigger);

            if (updated.getTriggerCondition() == WorkOrderMeterTriggerCondition.EVERY
                    && (previousCondition != WorkOrderMeterTriggerCondition.EVERY || updated.getLastTriggeredValue() == null)) {
                updated.setLastTriggeredValue(getLatestMeterReadingValue(updated.getMeter()));
            }

            return workOrderMeterTriggerRepository.save(updated);
        } else throw new CustomException("Not found", HttpStatus.NOT_FOUND);
    }

    public WorkOrderMeterTrigger save(WorkOrderMeterTrigger workOrderMeterTrigger) {
        return workOrderMeterTriggerRepository.save(workOrderMeterTrigger);
    }

    private double getLatestMeterReadingValue(Meter meter) {
        if (meter == null || meter.getId() == null) {
            return 0.0;
        }
        Collection<Reading> readings = readingRepository.findByMeter_Id(meter.getId());
        if (readings == null || readings.isEmpty()) {
            return 0.0;
        }
        return readings.stream()
                .max(Comparator.comparing(Reading::getCreatedAt).thenComparing(Reading::getId))
                .map(Reading::getValue)
                .orElse(0.0);
    }

    private void setMeterTriggerCustomFields(WorkOrderMeterTrigger workOrderMeterTrigger,
                                             List<CustomFieldValuePostDTO> customFieldValuePostDTOS,
                                             Company company) {
        customFieldValueService.setCustomFields(
                workOrderMeterTrigger,
                workOrderMeterTrigger.getCustomFieldValues(),
                customFieldValuePostDTOS,
                company,
                CustomFieldEntityType.WORK_ORDER,
                cfv -> cfv.setWorkOrderMeterTrigger(workOrderMeterTrigger)
        );
    }

    public Collection<WorkOrderMeterTrigger> getAll() {
        return workOrderMeterTriggerRepository.findAll();
    }

    public void delete(Long id) {
        workOrderMeterTriggerRepository.deleteById(id);
    }

    public Optional<WorkOrderMeterTrigger> findById(Long id) {
        return workOrderMeterTriggerRepository.findById(id);
    }

    public Collection<WorkOrderMeterTrigger> findByMeter(Long id) {
        return workOrderMeterTriggerRepository.findByMeter_Id(id);
    }
}
