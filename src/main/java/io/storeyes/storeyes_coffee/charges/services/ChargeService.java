package io.storeyes.storeyes_coffee.charges.services;

import io.storeyes.storeyes_coffee.charges.dto.*;
import io.storeyes.storeyes_coffee.charges.entities.*;
import io.storeyes.storeyes_coffee.alerts.auth.entities.UserPreference;
import io.storeyes.storeyes_coffee.alerts.auth.repositories.UserPreferenceRepository;
import io.storeyes.storeyes_coffee.charges.repositories.FixedChargeRepository;
import io.storeyes.storeyes_coffee.charges.repositories.PersonnelEmployeeRepository;
import io.storeyes.storeyes_coffee.charges.repositories.PersonnelWeekSalaryRepository;
import io.storeyes.storeyes_coffee.charges.repositories.VariableChargeRepository;
import io.storeyes.storeyes_coffee.charges.repositories.EmployeeRepository;
import io.storeyes.storeyes_coffee.charges.utils.WeekCalculationUtils;
import io.storeyes.storeyes_coffee.security.KeycloakTokenUtils;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.repositories.StoreRepository;
import io.storeyes.storeyes_coffee.store.services.StoreService;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChargeService {

    private final FixedChargeRepository fixedChargeRepository;
    private final PersonnelEmployeeRepository personnelEmployeeRepository;
    private final PersonnelWeekSalaryRepository personnelWeekSalaryRepository;
    private final VariableChargeRepository variableChargeRepository;
    private final StoreRepository storeRepository;
    private final StoreService storeService;
    private final EmployeeRepository employeeRepository;
    private final EntityManager entityManager;
    private final UserPreferenceRepository userPreferenceRepository;

    private static final BigDecimal THRESHOLD_PERCENTAGE = BigDecimal.valueOf(20);
    private static final int SCALE = 2;
    private static final int PRECISION_SCALE = 4;
    private static final String PREFERENCE_PERSONNEL_LAST_PERIOD = "personnel_charge_last_period";

    // ==================== Fixed Charges ====================

    /**
     * Get store ID from authenticated user
     */
    private Long getStoreId() {
        String userId = KeycloakTokenUtils.getUserId();
        if (userId == null) {
            throw new RuntimeException("User is not authenticated");
        }
        return storeService.getStoreByOwnerId(userId).getId();
    }

    /**
     * Get all fixed charges with optional filtering
     * Filters by authenticated user's store
     */
    public List<FixedChargeResponse> getAllFixedCharges(String month, ChargeCategory category, ChargePeriod period) {
        Long storeId = getStoreId();
        
        // Default to current month if not provided
        if (month == null || month.isEmpty()) {
            month = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }

        List<FixedCharge> charges;
        if (category != null && period != null) {
            charges = fixedChargeRepository.findByStoreIdAndCategoryAndMonthKeyAndPeriod(storeId, category, month, period);
        } else if (category != null) {
            charges = fixedChargeRepository.findByStoreIdAndCategoryAndMonthKey(storeId, category, month);
        } else if (period != null) {
            charges = fixedChargeRepository.findByStoreIdAndMonthKey(storeId, month).stream()
                    .filter(c -> c.getPeriod() == period)
                    .collect(Collectors.toList());
        } else {
            charges = fixedChargeRepository.findByStoreIdAndMonthKey(storeId, month);
        }

        return charges.stream()
                .map(this::toFixedChargeResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get fixed charge by ID with details
     * Verifies charge belongs to authenticated user's store
     */
    public FixedChargeDetailResponse getFixedChargeById(Long id, String month) {
        Long storeId = getStoreId();
        
        FixedCharge charge = fixedChargeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fixed charge not found with id: " + id));

        // Verify charge belongs to user's store
        if (!charge.getStore().getId().equals(storeId)) {
            throw new RuntimeException("Fixed charge not found with id: " + id);
        }

        return toFixedChargeDetailResponse(charge, month, storeId);
    }

    /**
     * Create a new fixed charge
     */
    @Transactional
    public FixedChargeResponse createFixedCharge(FixedChargeCreateRequest request) {
        // Validate request
        validateFixedChargeRequest(request);

        // Get store from authenticated user
        Long storeId = getStoreId();
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new RuntimeException("Store not found with id: " + storeId));

        // Build fixed charge
        FixedCharge charge = FixedCharge.builder()
                .store(store)
                .category(request.getCategory())
                .period(request.getPeriod())
                .monthKey(request.getMonthKey())
                .weekKey(request.getWeekKey())
                .notes(request.getNotes())
                .abnormalIncrease(false)
                .build();

        // Set custom name when category is OTHER
        if (request.getCategory() == ChargeCategory.OTHER && request.getName() != null) {
            charge.setName(request.getName().trim());
        }

        // Handle personnel charges with employees
        if (request.getCategory() == ChargeCategory.PERSONNEL) {
            if (request.getEmployees() == null || request.getEmployees().isEmpty()) {
                throw new RuntimeException("At least one employee is required for personnel charges");
            }

            // Process employees and calculate salaries
            List<PersonnelEmployee> employees = processEmployees(request.getEmployees(), charge, request.getPeriod(), request.getMonthKey(), request.getWeekKey());
            charge.setEmployees(employees);

            // Calculate total amount from employees if not provided
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal totalAmount = calculateTotalAmountFromEmployees(employees, request.getPeriod(), request.getWeekKey());
                charge.setAmount(totalAmount);
            } else {
                charge.setAmount(request.getAmount());
            }
        } else {
            // For non-personnel (utilities + OTHER), amount is required
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Amount is required for non-personnel charges");
            }
            charge.setAmount(request.getAmount());
        }

        // Calculate trend (for OTHER, trend is by same name)
        calculateAndSetTrend(charge, storeId);

        // Save charge (cascade will save employees)
        FixedCharge savedCharge = fixedChargeRepository.save(charge);

        return toFixedChargeResponse(savedCharge);
    }

    /**
     * Update fixed charge
     * Verifies charge belongs to authenticated user's store
     */
    @Transactional
    public FixedChargeResponse updateFixedCharge(Long id, FixedChargeUpdateRequest request) {
        Long storeId = getStoreId();
        
        FixedCharge charge = fixedChargeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fixed charge not found with id: " + id));

        // Verify charge belongs to user's store
        if (!charge.getStore().getId().equals(storeId)) {
            throw new RuntimeException("Fixed charge not found with id: " + id);
        }

        // Update fields if provided
        if (request.getAmount() != null) {
            charge.setAmount(request.getAmount());
        }
        if (request.getPeriod() != null) {
            charge.setPeriod(request.getPeriod());
        }
        if (request.getMonthKey() != null) {
            charge.setMonthKey(request.getMonthKey());
        }
        if (request.getWeekKey() != null) {
            charge.setWeekKey(request.getWeekKey());
        }
        if (request.getNotes() != null) {
            charge.setNotes(request.getNotes());
        }
        if (request.getName() != null && charge.getCategory() == ChargeCategory.OTHER) {
            charge.setName(request.getName().trim());
        }

        // Handle employee updates for personnel charges
        if (charge.getCategory() == ChargeCategory.PERSONNEL && request.getEmployees() != null) {
            ChargePeriod updatePeriod = request.getPeriod() != null ? request.getPeriod() : charge.getPeriod();
            ChargePeriod oldPeriod = charge.getPeriod();
            String updateMonthKey = request.getMonthKey() != null ? request.getMonthKey() : charge.getMonthKey();
            String updateWeekKey = request.getWeekKey() != null ? request.getWeekKey() : charge.getWeekKey();
            
            // Check if period changed - if so, treat as complete replacement
            boolean periodChanged = request.getPeriod() != null && !request.getPeriod().equals(oldPeriod);
            
            if (periodChanged) {
                // Period changed: Clear all existing PersonnelEmployee records and create fresh ones
                // This ensures no mixing of monthly/weekly salary structures
                
                // Explicitly clear week salaries first to avoid Hibernate orphan deletion issues
                List<PersonnelEmployee> existingEmployees = new ArrayList<>(charge.getEmployees());
                for (PersonnelEmployee emp : existingEmployees) {
                    if (emp.getWeekSalariesList() != null) {
                        emp.getWeekSalariesList().clear();
                    }
                }
                
                // Now clear all PersonnelEmployee records (cascade will handle week salary deletion)
                charge.getEmployees().clear();
                
                // Process all employees as new PersonnelEmployee records
                List<PersonnelEmployee> newEmployees = processEmployees(
                        request.getEmployees(),
                        charge,
                        updatePeriod,
                        updateMonthKey,
                        updateWeekKey
                );
                charge.setEmployees(newEmployees);
            } else {
                // Period unchanged: Match employees by Employee ID (from Employee entity, not PersonnelEmployee ID)
                // Create a map of existing PersonnelEmployee records by their Employee ID
                Map<Long, PersonnelEmployee> existingEmployeesByEmployeeIdMap = new HashMap<>();
                for (PersonnelEmployee emp : charge.getEmployees()) {
                    if (emp.getEmployee() != null && emp.getEmployee().getId() != null) {
                        existingEmployeesByEmployeeIdMap.put(emp.getEmployee().getId(), emp);
                    }
                }
                
                // Track which PersonnelEmployee records are being kept
                Set<Long> keptPersonnelEmployeeIds = new HashSet<>();
                
                // Process employees from request
                for (PersonnelEmployeeRequest empRequest : request.getEmployees()) {
                    Employee employeeEntity = null;
                    
                    // Get or find/create the Employee entity
                    if (empRequest.getId() != null) {
                        // Reusing existing Employee - find by Employee ID
                        employeeEntity = employeeRepository.findById(empRequest.getId())
                                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + empRequest.getId()));
                        
                        // Verify employee belongs to the same store
                        if (!employeeEntity.getStore().getId().equals(storeId)) {
                            throw new RuntimeException("Employee with id " + empRequest.getId() + " does not belong to your store");
                        }
                    } else {
                        // New or find existing Employee by name/type/position/startDate
                        if (empRequest.getName() == null || empRequest.getName().trim().isEmpty()) {
                            throw new RuntimeException("Employee name is required");
                        }
                        
                        Optional<Employee> existingEmployee = employeeRepository.findByStoreIdAndNameAndTypeAndStartDate(
                                storeId,
                                empRequest.getName().trim(),
                                empRequest.getType(),
                                empRequest.getStartDate());
                        
                        if (existingEmployee.isPresent()) {
                            employeeEntity = existingEmployee.get();
                        } else {
                            // Create new Employee entity
                            Store store = storeRepository.findById(storeId)
                                    .orElseThrow(() -> new RuntimeException("Store not found with id: " + storeId));
                            
                            employeeEntity = Employee.builder()
                                    .store(store)
                                    .name(empRequest.getName().trim())
                                    .type(empRequest.getType())
                                    .position(empRequest.getPosition())
                                    .startDate(empRequest.getStartDate())
                                    .build();
                            
                            employeeEntity = employeeRepository.save(employeeEntity);
                        }
                    }
                    
                    // Now handle PersonnelEmployee - check if one already exists for this Employee
                    PersonnelEmployee personnelEmployee = existingEmployeesByEmployeeIdMap.get(employeeEntity.getId());
                    
                    if (personnelEmployee != null) {
                        // Update existing PersonnelEmployee
                        keptPersonnelEmployeeIds.add(personnelEmployee.getId());
                        
                        // Update hours if provided
                        if (empRequest.getHours() != null) {
                            personnelEmployee.setHours(empRequest.getHours());
                        }
                        
                        // Process week salary updates if provided (for updating specific weeks in monthly charge)
                        if (empRequest.getWeekSalaries() != null && !empRequest.getWeekSalaries().isEmpty()) {
                            updateWeekSalaries(personnelEmployee, empRequest.getWeekSalaries(), updateMonthKey, updatePeriod);
                        }
                        
                        // Process new salary if provided
                        if (empRequest.getSalary() != null && empRequest.getSalary().compareTo(BigDecimal.ZERO) > 0) {
                            if (updatePeriod == ChargePeriod.MONTH) {
                                // Monthly update - redistribute across all weeks
                                distributeMonthlySalary(personnelEmployee, empRequest.getSalary(), updateMonthKey);
                            } else if (updatePeriod == ChargePeriod.WEEK && updateWeekKey != null) {
                                // Weekly update - update specific week, preserve others
                                setWeeklySalary(personnelEmployee, empRequest.getSalary(), updateWeekKey, updateMonthKey);
                            }
                        }
                    } else {
                        // Create new PersonnelEmployee linked to the Employee entity
                        personnelEmployee = PersonnelEmployee.builder()
                                .fixedCharge(charge)
                                .employee(employeeEntity)
                                .name(employeeEntity.getName())
                                .type(employeeEntity.getType())
                                .position(employeeEntity.getPosition())
                                .startDate(employeeEntity.getStartDate())
                                .hours(empRequest.getHours())
                                .build();
                        
                        // Process salary - required
                        if (empRequest.getSalary() == null || empRequest.getSalary().compareTo(BigDecimal.ZERO) <= 0) {
                            throw new RuntimeException("Employee salary is required and must be greater than zero for employee: " + employeeEntity.getName());
                        }
                        
                        if (updatePeriod == ChargePeriod.MONTH) {
                            if (updateMonthKey == null || updateMonthKey.trim().isEmpty()) {
                                throw new RuntimeException("Month key is required for monthly personnel charges");
                            }
                            distributeMonthlySalary(personnelEmployee, empRequest.getSalary(), updateMonthKey);
                        } else if (updatePeriod == ChargePeriod.WEEK) {
                            if (updateWeekKey == null || updateWeekKey.trim().isEmpty()) {
                                throw new RuntimeException("Week key is required for weekly personnel charges");
                            }
                            if (updateMonthKey == null || updateMonthKey.trim().isEmpty()) {
                                throw new RuntimeException("Month key is required for weekly personnel charges");
                            }
                            setWeeklySalary(personnelEmployee, empRequest.getSalary(), updateWeekKey, updateMonthKey);
                        }
                        
                        charge.getEmployees().add(personnelEmployee);
                    }
                }
                
                // Remove PersonnelEmployee records that are no longer in the request
                charge.getEmployees().removeIf(emp -> emp.getId() != null && !keptPersonnelEmployeeIds.contains(emp.getId()));
            }

            // Recalculate amount if not provided
            if (request.getAmount() == null) {
                BigDecimal totalAmount = calculateTotalAmountFromEmployees(
                        charge.getEmployees(),
                        request.getPeriod() != null ? request.getPeriod() : charge.getPeriod(),
                        request.getWeekKey() != null ? request.getWeekKey() : charge.getWeekKey()
                );
                charge.setAmount(totalAmount);
            }
        }

        // Recalculate trend
        calculateAndSetTrend(charge, storeId);

        // Save updated charge
        FixedCharge updatedCharge = fixedChargeRepository.save(charge);

        return toFixedChargeResponse(updatedCharge);
    }

    /**
     * Delete fixed charge
     * Verifies charge belongs to authenticated user's store
     */
    @Transactional
    public void deleteFixedCharge(Long id) {
        Long storeId = getStoreId();
        
        FixedCharge charge = fixedChargeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fixed charge not found with id: " + id));

        // Verify charge belongs to user's store
        if (!charge.getStore().getId().equals(storeId)) {
            throw new RuntimeException("Fixed charge not found with id: " + id);
        }

        fixedChargeRepository.deleteById(id); // Cascade will delete employees
    }

    /**
     * Get fixed charges by month
     * Filters by authenticated user's store
     */
    public List<FixedChargeResponse> getFixedChargesByMonth(String monthKey) {
        Long storeId = getStoreId();
        List<FixedCharge> charges = fixedChargeRepository.findByStoreIdAndMonthKey(storeId, monthKey);
        return charges.stream()
                .map(this::toFixedChargeResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get fixed charges by week for a specific month
     * Filters by authenticated user's store
     * Useful for displaying week-by-week breakdown of personnel charges
     */
    public List<FixedChargeResponse> getFixedChargesByWeek(String monthKey, String weekKey, ChargeCategory category) {
        Long storeId = getStoreId();
        List<FixedCharge> charges;
        
        if (category != null) {
            charges = fixedChargeRepository.findByStoreIdAndCategoryAndMonthKeyAndWeekKey(storeId, category, monthKey, weekKey);
        } else {
            charges = fixedChargeRepository.findByStoreIdAndMonthKeyAndWeekKey(storeId, monthKey, weekKey);
        }
        
        return charges.stream()
                .map(this::toFixedChargeResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get available employees for reuse
     * Returns Employee entities from the employees table (reusable employees)
     * Filters by authenticated user's store
     */
    public List<PersonnelEmployeeResponse> getAvailableEmployees(EmployeeType type) {
        Long storeId = getStoreId();
        List<Employee> employees = employeeRepository.findByStoreIdAndType(storeId, type);
        
        // Convert Employee entities to PersonnelEmployeeResponse
        return employees.stream()
                .map(emp -> PersonnelEmployeeResponse.builder()
                        .id(emp.getId())
                        .name(emp.getName())
                        .type(emp.getType())
                        .position(emp.getPosition())
                        .startDate(emp.getStartDate())
                        .build())
                .collect(Collectors.toList());
    }

    // ==================== Variable Charges ====================

    /**
     * Get all variable charges with optional filtering
     * Filters by authenticated user's store
     */
    public List<VariableChargeResponse> getAllVariableCharges(LocalDate startDate, LocalDate endDate, String category) {
        Long storeId = getStoreId();
        List<VariableCharge> charges;
        
        // Handle null parameters - JPQL doesn't handle IS NULL well with parameters
        if (startDate != null && endDate != null) {
            // Both dates provided
            if (category != null && !category.isEmpty()) {
                // Filter by category as well
                charges = variableChargeRepository.findByStoreIdAndDateRangeAndCategory(storeId, startDate, endDate, category);
            } else {
                // No category filter
                charges = variableChargeRepository.findByStoreIdAndDateRange(storeId, startDate, endDate);
            }
        } else if (category != null && !category.isEmpty()) {
            // Only category filter, no date range
            charges = variableChargeRepository.findByStoreIdAndCategory(storeId, category);
        } else {
            // No filters, get all charges for store
            charges = variableChargeRepository.findByStoreIdOrderByDateDesc(storeId);
        }
        
        return charges.stream()
                .map(this::toVariableChargeResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get variable charge by ID
     * Verifies charge belongs to authenticated user's store
     */
    public VariableChargeResponse getVariableChargeById(Long id) {
        Long storeId = getStoreId();
        
        VariableCharge charge = variableChargeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Variable charge not found with id: " + id));

        // Verify charge belongs to user's store
        if (!charge.getStore().getId().equals(storeId)) {
            throw new RuntimeException("Variable charge not found with id: " + id);
        }

        return toVariableChargeResponse(charge);
    }

    /**
     * Create variable charge
     */
    @Transactional
    public VariableChargeResponse createVariableCharge(VariableChargeCreateRequest request) {
        // Get store from authenticated user
        Long storeId = getStoreId();
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new RuntimeException("Store not found with id: " + storeId));

        VariableCharge charge = VariableCharge.builder()
                .store(store)
                .name(request.getName())
                .amount(request.getAmount())
                .date(request.getDate())
                .category(request.getCategory())
                .supplier(request.getSupplier())
                .notes(request.getNotes())
                .purchaseOrderUrl(request.getPurchaseOrderUrl())
                .build();

        VariableCharge savedCharge = variableChargeRepository.save(charge);
        return toVariableChargeResponse(savedCharge);
    }

    /**
     * Update variable charge
     * Verifies charge belongs to authenticated user's store
     */
    @Transactional
    public VariableChargeResponse updateVariableCharge(Long id, VariableChargeUpdateRequest request) {
        Long storeId = getStoreId();
        
        VariableCharge charge = variableChargeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Variable charge not found with id: " + id));

        // Verify charge belongs to user's store
        if (!charge.getStore().getId().equals(storeId)) {
            throw new RuntimeException("Variable charge not found with id: " + id);
        }

        if (request.getName() != null) {
            charge.setName(request.getName());
        }
        if (request.getAmount() != null) {
            charge.setAmount(request.getAmount());
        }
        if (request.getDate() != null) {
            charge.setDate(request.getDate());
        }
        if (request.getCategory() != null) {
            charge.setCategory(request.getCategory());
        }
        if (request.getSupplier() != null) {
            charge.setSupplier(request.getSupplier());
        }
        if (request.getNotes() != null) {
            charge.setNotes(request.getNotes());
        }
        if (request.getPurchaseOrderUrl() != null) {
            charge.setPurchaseOrderUrl(request.getPurchaseOrderUrl());
        }

        VariableCharge updatedCharge = variableChargeRepository.save(charge);
        return toVariableChargeResponse(updatedCharge);
    }

    /**
     * Delete variable charge
     * Verifies charge belongs to authenticated user's store
     */
    @Transactional
    public void deleteVariableCharge(Long id) {
        Long storeId = getStoreId();
        
        VariableCharge charge = variableChargeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Variable charge not found with id: " + id));

        // Verify charge belongs to user's store
        if (!charge.getStore().getId().equals(storeId)) {
            throw new RuntimeException("Variable charge not found with id: " + id);
        }

        variableChargeRepository.deleteById(id);
    }

    // ==================== Salary Calculation Logic ====================

    /**
     * Process employees: create new or reuse existing, calculate salary distributions
     * - If employee ID is provided, finds the Employee entity and reuses it
     * - If no ID, finds or creates an Employee entity, then creates PersonnelEmployee linked to it
     */
    private List<PersonnelEmployee> processEmployees(
            List<PersonnelEmployeeRequest> employeeRequests,
            FixedCharge charge,
            ChargePeriod period,
            String monthKey,
            String weekKey) {

        List<PersonnelEmployee> personnelEmployees = new ArrayList<>();
        Long storeId = charge.getStore().getId();

        for (PersonnelEmployeeRequest empRequest : employeeRequests) {
            Employee employeeEntity;
            
            if (empRequest.getId() != null) {
                // Reuse existing employee - find Employee entity by ID
                employeeEntity = employeeRepository.findById(empRequest.getId())
                        .orElseThrow(() -> new RuntimeException("Employee not found with id: " + empRequest.getId()));
                
                // Verify employee belongs to the same store
                if (!employeeEntity.getStore().getId().equals(storeId)) {
                    throw new RuntimeException("Employee with id " + empRequest.getId() + " does not belong to your store");
                }
            } else {
                // Create new employee or find existing one based on name/type/position/startDate
                // Validate name is provided and not empty
                if (empRequest.getName() == null || empRequest.getName().trim().isEmpty()) {
                    throw new RuntimeException("Employee name is required");
                }
                
                String employeeName = empRequest.getName().trim();
                
                // Try to find existing employee with same details
                Optional<Employee> existingEmployee = employeeRepository.findByStoreIdAndNameAndTypeAndStartDate(
                        storeId,
                        employeeName,
                        empRequest.getType(),
                        empRequest.getStartDate());
                
                if (existingEmployee.isPresent()) {
                    // Reuse existing employee
                    employeeEntity = existingEmployee.get();
                } else {
                    // Create new Employee entity (reusable)
                    Store store = storeRepository.findById(storeId)
                            .orElseThrow(() -> new RuntimeException("Store not found with id: " + storeId));
                    
                    employeeEntity = Employee.builder()
                            .store(store)
                            .name(employeeName)
                            .type(empRequest.getType())
                            .position(empRequest.getPosition())
                            .startDate(empRequest.getStartDate())
                            .build();
                    
                    // Save the new Employee entity
                    employeeEntity = employeeRepository.save(employeeEntity);
                }
            }

            // Now create PersonnelEmployee linked to the Employee entity
            PersonnelEmployee personnelEmployee = PersonnelEmployee.builder()
                    .fixedCharge(charge)
                    .employee(employeeEntity)
                    .name(employeeEntity.getName())
                    .type(employeeEntity.getType())
                    .position(employeeEntity.getPosition())
                    .startDate(employeeEntity.getStartDate())
                    .hours(empRequest.getHours())
                    .build();

            // Process salary - required for personnel charges
            if (empRequest.getSalary() == null || empRequest.getSalary().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Employee salary is required and must be greater than zero for employee: " + employeeEntity.getName());
            }

            // Validate monthKey for monthly charges
            if (period == ChargePeriod.MONTH) {
                if (monthKey == null || monthKey.trim().isEmpty()) {
                    throw new RuntimeException("Month key is required for monthly personnel charges");
                }
                distributeMonthlySalary(personnelEmployee, empRequest.getSalary(), monthKey);
            } else if (period == ChargePeriod.WEEK) {
                if (weekKey == null || weekKey.trim().isEmpty()) {
                    throw new RuntimeException("Week key is required for weekly personnel charges");
                }
                if (monthKey == null || monthKey.trim().isEmpty()) {
                    throw new RuntimeException("Month key is required for weekly personnel charges");
                }
                setWeeklySalary(personnelEmployee, empRequest.getSalary(), weekKey, monthKey);
            }

            personnelEmployees.add(personnelEmployee);
        }

        return personnelEmployees;
    }

    /**
     * Distribute monthly salary across weeks that belong to the month
     * Uses ISO 8601 week structure (Monday-Sunday) where weeks belong to the month where Monday falls
     * All weeks are treated as full weeks (7 days each), so salary is distributed equally
     * 
     * IMPORTANT: Explicitly deletes existing week salaries for this month before creating new ones
     * to avoid Hibernate orphan deletion issues.
     */
    private void distributeMonthlySalary(PersonnelEmployee employee, BigDecimal monthlySalary, String monthKey) {
        // Validate monthKey
        if (monthKey == null || monthKey.trim().isEmpty()) {
            throw new RuntimeException("Month key cannot be null or empty when distributing monthly salary");
        }
        
        // Validate monthlySalary
        if (monthlySalary == null || monthlySalary.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Monthly salary must be greater than zero");
        }
        
        // Ensure employee is managed by Hibernate and explicitly delete existing week salaries
        PersonnelEmployee employeeToUse = employee;
        if (employee.getId() != null) {
            // Ensure entity is managed - use merge to get the managed instance with proper collection state
            employeeToUse = entityManager.merge(employee);
            
            // Force initialization of the collection to ensure it's attached to Hibernate
            if (employeeToUse.getWeekSalariesList() == null) {
                List<PersonnelWeekSalary> newList = new ArrayList<>();
                employeeToUse.setWeekSalariesList(newList);
            } else {
                // Touch the collection to ensure it's initialized
                employeeToUse.getWeekSalariesList().size();
            }
            
            // Get the existing collection (don't create a new one - work with Hibernate's managed collection)
            List<PersonnelWeekSalary> existingWeekSalariesList = employeeToUse.getWeekSalariesList();
            
            // Create a copy of existing week salaries to avoid concurrent modification
            List<PersonnelWeekSalary> weekSalariesToDelete = new ArrayList<>();
            for (PersonnelWeekSalary ws : existingWeekSalariesList) {
                if (ws.getMonthKey() != null && ws.getMonthKey().equals(monthKey)) {
                    weekSalariesToDelete.add(ws);
                }
            }
            
            // Explicitly delete week salaries that belong to this month FIRST
            boolean deletedAny = false;
            for (PersonnelWeekSalary weekSalary : weekSalariesToDelete) {
                personnelWeekSalaryRepository.delete(weekSalary);
                deletedAny = true;
            }
            
            // Remove from collection (modify in place, don't replace)
            existingWeekSalariesList.removeAll(weekSalariesToDelete);
            
            // Flush to ensure deletions are committed (only if we deleted something)
            if (deletedAny) {
                entityManager.flush();
            }
        } else {
            // For new employees (not yet persisted), initialize collection if needed
            if (employee.getWeekSalariesList() == null) {
                employee.setWeekSalariesList(new ArrayList<>());
            } else {
                // Remove existing week salaries for this month from in-memory collection
                employee.getWeekSalariesList().removeIf(ws -> 
                    ws.getMonthKey() != null && ws.getMonthKey().equals(monthKey));
            }
        }
        
        // Use WeekCalculationUtils to distribute salary across weeks
        Map<String, BigDecimal> weekSalariesMap = WeekCalculationUtils.distributeMonthlySalary(monthlySalary, monthKey);
        
        // Validate that we got at least one week
        if (weekSalariesMap.isEmpty()) {
            throw new RuntimeException("No weeks found for month key: " + monthKey + ". Please verify the month key format is YYYY-MM");
        }

        // Create new PersonnelWeekSalary records for each week and add to existing collection
        List<PersonnelWeekSalary> weekSalariesList = employeeToUse.getWeekSalariesList();
        if (weekSalariesList == null) {
            weekSalariesList = new ArrayList<>();
            employeeToUse.setWeekSalariesList(weekSalariesList);
        }
        
        for (Map.Entry<String, BigDecimal> entry : weekSalariesMap.entrySet()) {
            String weekKey = entry.getKey();
            BigDecimal amount = entry.getValue();
            
            // Get month key for this week (where Monday falls)
            String weekMonthKey = WeekCalculationUtils.getMonthKeyForWeek(weekKey);
            
            PersonnelWeekSalary weekSalary = PersonnelWeekSalary.builder()
                    .personnelEmployee(employeeToUse)
                    .weekKey(weekKey)
                    .amount(amount)
                    .daysInMonth(7) // Always 7 for weeks that belong to the month (full week)
                    .monthKey(weekMonthKey)
                    .build();
            
            // Add to existing collection (don't replace it)
            weekSalariesList.add(weekSalary);
        }

        // Update employee fields - don't call setWeekSalariesList since we modified the collection in place
        // weekSalariesList is already the same reference as employeeToUse.getWeekSalariesList()
        employeeToUse.setMonthSalary(monthlySalary);
        employeeToUse.setWeekSalaries(weekSalariesMap); // Transient map for easier access
        employeeToUse.setSalary(monthlySalary);
        employeeToUse.setSalaryByPeriod(SalaryByPeriod.MONTH);
        
        // Calculate average week salary for backward compatibility
        if (!weekSalariesMap.isEmpty()) {
            BigDecimal avgWeekSalary = monthlySalary.divide(
                    BigDecimal.valueOf(weekSalariesMap.size()),
                    SCALE,
                    RoundingMode.HALF_UP
            );
            employeeToUse.setWeekSalary(avgWeekSalary); // For backward compatibility
        }
        
        // Also update the original employee reference for consistency (only if different from managed)
        // Don't set collection fields on the parameter to avoid Hibernate issues
        if (employee != employeeToUse) {
            employee.setMonthSalary(monthlySalary);
            employee.setSalary(monthlySalary);
            employee.setSalaryByPeriod(SalaryByPeriod.MONTH);
            employee.setWeekSalaries(weekSalariesMap); // Transient map
            if (!weekSalariesMap.isEmpty()) {
                BigDecimal avgWeekSalary = monthlySalary.divide(
                        BigDecimal.valueOf(weekSalariesMap.size()),
                        SCALE,
                        RoundingMode.HALF_UP
                );
                employee.setWeekSalary(avgWeekSalary);
            }
            employee.setDaysLeftSalary(null);
        }
        
        // Deprecated fields - set to null or zero
        employeeToUse.setDaysLeftSalary(null);
        
        // Calculate average week salary for backward compatibility
        if (!weekSalariesMap.isEmpty()) {
            BigDecimal avgWeekSalary = monthlySalary.divide(
                    BigDecimal.valueOf(weekSalariesMap.size()),
                    SCALE,
                    RoundingMode.HALF_UP
            );
            employee.setWeekSalary(avgWeekSalary); // For backward compatibility
        }
        
        // Deprecated fields - set to null or zero
        employee.setDaysLeftSalary(null);
    }

    /**
     * Update specific week salaries for an employee
     * Used when updating individual week salaries within a monthly charge
     * Updates the specified weeks and recalculates the total month salary
     * 
     * IMPORTANT: Explicitly deletes existing week salaries for the specified weeks before creating/updating
     * to avoid Hibernate orphan deletion issues.
     */
    private void updateWeekSalaries(PersonnelEmployee employee, Map<String, BigDecimal> weekSalariesUpdate, String monthKey, ChargePeriod period) {
        // Collect week keys that need to be updated
        Set<String> weekKeysToUpdate = weekSalariesUpdate.keySet();
        
        // Ensure employee is managed and explicitly delete existing week salaries
        PersonnelEmployee employeeToUse = employee;
        if (employee.getId() != null) {
            // Ensure entity is managed - use merge to get the managed instance with proper collection state
            employeeToUse = entityManager.merge(employee);
            
            // Force initialization of the collection to ensure it's attached to Hibernate
            if (employeeToUse.getWeekSalariesList() == null) {
                List<PersonnelWeekSalary> newList = new ArrayList<>();
                employeeToUse.setWeekSalariesList(newList);
            } else {
                // Touch the collection to ensure it's initialized
                employeeToUse.getWeekSalariesList().size();
            }
            
            // Get the existing collection (don't create a new one - work with Hibernate's managed collection)
            List<PersonnelWeekSalary> existingWeekSalariesList = employeeToUse.getWeekSalariesList();
            
            // Create a copy of week salaries to delete to avoid concurrent modification
            List<PersonnelWeekSalary> weekSalariesToDelete = new ArrayList<>();
            for (PersonnelWeekSalary ws : existingWeekSalariesList) {
                if (weekKeysToUpdate.contains(ws.getWeekKey())) {
                    weekSalariesToDelete.add(ws);
                }
            }
            
            // Explicitly delete existing week salaries for the weeks being updated
            boolean deletedAny = false;
            for (PersonnelWeekSalary weekSalary : weekSalariesToDelete) {
                personnelWeekSalaryRepository.delete(weekSalary);
                deletedAny = true;
            }
            
            // Remove from collection (modify in place, don't replace)
            existingWeekSalariesList.removeAll(weekSalariesToDelete);
            
            // Flush to ensure deletions are committed (only if we deleted something)
            if (deletedAny) {
                entityManager.flush();
            }
        } else {
            // For new employees (not yet persisted), initialize collection if needed
            if (employee.getWeekSalariesList() == null) {
                employee.setWeekSalariesList(new ArrayList<>());
            } else {
                // Remove from in-memory collection
                employee.getWeekSalariesList().removeIf(ws -> weekKeysToUpdate.contains(ws.getWeekKey()));
            }
        }
        
        // Get the collection to add to (use existing, don't create new)
        List<PersonnelWeekSalary> weekSalariesList = employeeToUse.getWeekSalariesList();
        if (weekSalariesList == null) {
            weekSalariesList = new ArrayList<>();
            employeeToUse.setWeekSalariesList(weekSalariesList);
        }
        
        // Create new week salary records for each week being updated and add to existing collection
        for (Map.Entry<String, BigDecimal> entry : weekSalariesUpdate.entrySet()) {
            String weekKey = entry.getKey();
            BigDecimal amount = entry.getValue();

            // Validate week key format
            if (!WeekCalculationUtils.isValidWeekKey(weekKey)) {
                throw new RuntimeException("Invalid week key: " + weekKey + ". Week key must be a Monday date in format YYYY-MM-DD");
            }

            // Validate that week overlaps with the month key if provided
            if (monthKey != null && !WeekCalculationUtils.weekOverlapsWithMonth(weekKey, monthKey)) {
                throw new RuntimeException("Week key " + weekKey + " does not overlap with month key " + monthKey);
            }

            // Get month key for this week (where Monday falls)
            String weekMonthKey = WeekCalculationUtils.getMonthKeyForWeek(weekKey);

            // Create new week salary record (old one was already deleted)
            PersonnelWeekSalary newWeekSalary = PersonnelWeekSalary.builder()
                    .personnelEmployee(employeeToUse)
                    .weekKey(weekKey)
                    .amount(amount)
                    .daysInMonth(7) // Always 7 for full weeks
                    .monthKey(weekMonthKey)
                    .build();
            
            // Add to existing collection (don't replace it)
            weekSalariesList.add(newWeekSalary);
        }

        // Rebuild week salaries map for transient access
        Map<String, BigDecimal> weekSalariesMap = weekSalariesList.stream()
                .collect(Collectors.toMap(
                        PersonnelWeekSalary::getWeekKey,
                        PersonnelWeekSalary::getAmount,
                        (existing, replacement) -> replacement
                ));

        // Calculate total month salary from all week salaries that belong to the month
        BigDecimal totalMonthSalary = BigDecimal.ZERO;
        if (monthKey != null) {
            totalMonthSalary = weekSalariesList.stream()
                    .filter(ws -> ws.getMonthKey().equals(monthKey))
                    .map(PersonnelWeekSalary::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            // If no month key provided, sum all week salaries
            totalMonthSalary = weekSalariesList.stream()
                    .map(PersonnelWeekSalary::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // Update employee fields - use employeeToUse (managed entity) to avoid collection replacement issues
        // Don't call setWeekSalariesList since we modified the collection in place
        employeeToUse.setWeekSalaries(weekSalariesMap); // Transient map
        employeeToUse.setMonthSalary(totalMonthSalary);
        employeeToUse.setSalary(totalMonthSalary); // Use total month salary
        employeeToUse.setSalaryByPeriod(period != null ? (period == ChargePeriod.MONTH ? SalaryByPeriod.MONTH : SalaryByPeriod.WEEK) : employeeToUse.getSalaryByPeriod());
        
        // Calculate average week salary for backward compatibility
        if (!weekSalariesMap.isEmpty()) {
            BigDecimal avgWeekSalary = totalMonthSalary.divide(
                    BigDecimal.valueOf(weekSalariesMap.size()),
                    SCALE,
                    RoundingMode.HALF_UP
            );
            employeeToUse.setWeekSalary(avgWeekSalary);
        }
        
        // Also update the original employee reference for consistency (only if different from managed)
        // Don't set collection fields on the parameter to avoid Hibernate issues
        if (employee != employeeToUse) {
            employee.setMonthSalary(totalMonthSalary);
            employee.setSalary(totalMonthSalary);
            employee.setSalaryByPeriod(employeeToUse.getSalaryByPeriod());
            employee.setWeekSalaries(weekSalariesMap); // Transient map
            if (!weekSalariesMap.isEmpty()) {
                BigDecimal avgWeekSalary = totalMonthSalary.divide(
                        BigDecimal.valueOf(weekSalariesMap.size()),
                        SCALE,
                        RoundingMode.HALF_UP
                );
                employee.setWeekSalary(avgWeekSalary);
            }
            employee.setDaysLeftSalary(null);
        }
        
        // Deprecated fields
        employeeToUse.setDaysLeftSalary(null);
    }

    /**
     * Set weekly salary for a specific week
     * Validates week key format (must be Monday date: YYYY-MM-DD)
     * 
     * IMPORTANT: Explicitly deletes existing week salary for this week before creating new one
     * to avoid Hibernate orphan deletion issues.
     */
    private void setWeeklySalary(PersonnelEmployee employee, BigDecimal weeklySalary, String weekKey, String monthKey) {
        // Validate week key
        if (weekKey == null || weekKey.trim().isEmpty()) {
            throw new RuntimeException("Week key cannot be null or empty for weekly salary");
        }
        
        // Validate month key
        if (monthKey == null || monthKey.trim().isEmpty()) {
            throw new RuntimeException("Month key cannot be null or empty for weekly salary");
        }
        
        // Validate weekly salary
        if (weeklySalary == null || weeklySalary.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Weekly salary must be greater than zero");
        }
        
        // Validate week key format (must be a Monday date)
        if (!WeekCalculationUtils.isValidWeekKey(weekKey)) {
            throw new RuntimeException("Invalid week key: " + weekKey + ". Week key must be a Monday date in format YYYY-MM-DD");
        }
        
        // Validate that week overlaps with the provided month
        if (!WeekCalculationUtils.weekOverlapsWithMonth(weekKey, monthKey)) {
            throw new RuntimeException("Week key " + weekKey + " does not overlap with the provided month key " + monthKey);
        }

        // Ensure employee is managed and explicitly delete existing week salary if it exists
        PersonnelEmployee employeeToUse = employee;
        if (employee.getId() != null) {
            // Ensure entity is managed - use merge to get the managed instance with proper collection state
            employeeToUse = entityManager.merge(employee);
            
            // Force initialization of the collection to ensure it's attached to Hibernate
            if (employeeToUse.getWeekSalariesList() == null) {
                List<PersonnelWeekSalary> newList = new ArrayList<>();
                employeeToUse.setWeekSalariesList(newList);
            } else {
                // Touch the collection to ensure it's initialized
                employeeToUse.getWeekSalariesList().size();
            }
            
            // Explicitly delete existing week salary for this week if it exists
            Optional<PersonnelWeekSalary> existingWeekSalaryOpt = 
                    personnelWeekSalaryRepository.findByPersonnelEmployeeIdAndWeekKey(
                            employeeToUse.getId(), 
                            weekKey);
            
            if (existingWeekSalaryOpt.isPresent()) {
                PersonnelWeekSalary existingWeekSalary = existingWeekSalaryOpt.get();
                // Explicitly delete to avoid orphan deletion issues
                personnelWeekSalaryRepository.delete(existingWeekSalary);
                
                // Remove from collection (modify in place, don't replace)
                employeeToUse.getWeekSalariesList().remove(existingWeekSalary);
                
                // Flush to ensure deletion is committed
                entityManager.flush();
            }
        } else {
            // For new employees (not yet persisted), initialize collection if needed
            if (employee.getWeekSalariesList() == null) {
                employee.setWeekSalariesList(new ArrayList<>());
            } else {
                // Remove from in-memory collection
                employee.getWeekSalariesList().removeIf(ws -> ws.getWeekKey().equals(weekKey));
            }
        }

        // Get month key for this week (where Monday falls)
        String weekMonthKey = WeekCalculationUtils.getMonthKeyForWeek(weekKey);

        // Create new week salary record (old one was already deleted)
        PersonnelWeekSalary newWeekSalary = PersonnelWeekSalary.builder()
                .personnelEmployee(employeeToUse)
                .weekKey(weekKey)
                .amount(weeklySalary)
                .daysInMonth(7) // Always 7 for full weeks
                .monthKey(weekMonthKey)
                .build();
        
        // Get existing week salaries list and add to it (don't replace the collection)
        List<PersonnelWeekSalary> weekSalariesList = employeeToUse.getWeekSalariesList();
        if (weekSalariesList == null) {
            weekSalariesList = new ArrayList<>();
            employeeToUse.setWeekSalariesList(weekSalariesList);
        }
        weekSalariesList.add(newWeekSalary);

        // Build week salaries map for transient access
        Map<String, BigDecimal> weekSalariesMap = weekSalariesList.stream()
                .collect(Collectors.toMap(
                        PersonnelWeekSalary::getWeekKey,
                        PersonnelWeekSalary::getAmount,
                        (existing, replacement) -> replacement
                ));

        // Calculate month total from all week salaries in the provided month
        BigDecimal totalWeekSalaries = weekSalariesList.stream()
                .filter(ws -> ws.getMonthKey().equals(monthKey))
                .map(PersonnelWeekSalary::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Set employee fields - don't call setWeekSalariesList since we modified the collection in place
        // weekSalariesList is already the same reference as employeeToUse.getWeekSalariesList()
        employeeToUse.setWeekSalaries(weekSalariesMap); // Transient map
        employeeToUse.setWeekSalary(weeklySalary); // For display
        employeeToUse.setMonthSalary(totalWeekSalaries);
        employeeToUse.setSalary(weeklySalary); // For display
        employeeToUse.setSalaryByPeriod(SalaryByPeriod.WEEK);
        
        // Also update the original employee reference for consistency (only if different from managed)
        // Don't set collection fields on the parameter to avoid Hibernate issues
        if (employee != employeeToUse) {
            employee.setWeekSalary(weeklySalary);
            employee.setMonthSalary(totalWeekSalaries);
            employee.setSalary(weeklySalary);
            employee.setSalaryByPeriod(SalaryByPeriod.WEEK);
            employee.setWeekSalaries(weekSalariesMap); // Transient map
            employee.setDaysLeftSalary(null);
        }
        
        // Deprecated fields
        employeeToUse.setDaysLeftSalary(null);
    }

    /**
     * Calculate total amount from employees
     * Uses PersonnelWeekSalary records for accurate calculation
     */
    private BigDecimal calculateTotalAmountFromEmployees(List<PersonnelEmployee> employees, ChargePeriod period, String weekKey) {
        BigDecimal total = BigDecimal.ZERO;

        for (PersonnelEmployee emp : employees) {
            BigDecimal empAmount = BigDecimal.ZERO;

            if (period == ChargePeriod.MONTH) {
                // For monthly period, use monthSalary if exists
                // Otherwise, calculate from PersonnelWeekSalary records
                if (emp.getMonthSalary() != null) {
                    empAmount = emp.getMonthSalary();
                } else if (emp.getWeekSalariesList() != null && !emp.getWeekSalariesList().isEmpty()) {
                    // Sum all week salaries
                    empAmount = emp.getWeekSalariesList().stream()
                            .map(PersonnelWeekSalary::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                } else if (emp.getWeekSalaries() != null && !emp.getWeekSalaries().isEmpty()) {
                    // Fallback to transient map (backward compatibility)
                    empAmount = emp.getWeekSalaries().values().stream()
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                } else if (emp.getSalary() != null) {
                    empAmount = emp.getSalary();
                }
            } else if (period == ChargePeriod.WEEK && weekKey != null) {
                // For weekly period, find specific week salary
                if (emp.getWeekSalariesList() != null && !emp.getWeekSalariesList().isEmpty()) {
                    // Use PersonnelWeekSalary records
                    Optional<PersonnelWeekSalary> weekSalaryOpt = emp.getWeekSalariesList().stream()
                            .filter(ws -> ws.getWeekKey().equals(weekKey))
                            .findFirst();
                    if (weekSalaryOpt.isPresent()) {
                        empAmount = weekSalaryOpt.get().getAmount();
                    }
                } else if (emp.getWeekSalaries() != null && emp.getWeekSalaries().containsKey(weekKey)) {
                    // Fallback to transient map (backward compatibility)
                    empAmount = emp.getWeekSalaries().get(weekKey);
                } else if (emp.getWeekSalary() != null) {
                    empAmount = emp.getWeekSalary();
                } else if (emp.getSalary() != null) {
                    empAmount = emp.getSalary();
                }
            }

            total = total.add(empAmount);
        }

        return total.setScale(SCALE, RoundingMode.HALF_UP);
    }

    // ==================== Trend Calculation ====================

    /**
     * Calculate and set trend for a fixed charge
     * For OTHER category, compares to previous charges with the same custom name
     */
    private void calculateAndSetTrend(FixedCharge charge, Long storeId) {
        List<FixedCharge> previousCharges;
        if (charge.getCategory() == ChargeCategory.OTHER && charge.getName() != null && !charge.getName().trim().isEmpty()) {
            previousCharges = fixedChargeRepository.findPreviousChargesWithName(
                    storeId,
                    charge.getCategory(),
                    charge.getPeriod(),
                    charge.getName().trim(),
                    charge.getMonthKey(),
                    charge.getWeekKey() != null ? charge.getWeekKey() : ""
            );
        } else {
            previousCharges = fixedChargeRepository.findPreviousCharges(
                    storeId,
                    charge.getCategory(),
                    charge.getPeriod(),
                    charge.getMonthKey(),
                    charge.getWeekKey() != null ? charge.getWeekKey() : ""
            );
        }

        if (!previousCharges.isEmpty()) {
            FixedCharge previousCharge = previousCharges.get(0);
            BigDecimal previousAmount = previousCharge.getAmount();
            BigDecimal currentAmount = charge.getAmount();

            // Calculate difference
            BigDecimal difference = currentAmount.subtract(previousAmount);

            // Calculate percentage
            BigDecimal percentage = BigDecimal.ZERO;
            if (previousAmount.compareTo(BigDecimal.ZERO) > 0) {
                percentage = difference.divide(previousAmount, PRECISION_SCALE, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(SCALE, RoundingMode.HALF_UP);
            }

            // Determine trend direction
            TrendDirection trend;
            if (difference.compareTo(BigDecimal.ZERO) > 0) {
                trend = TrendDirection.UP;
            } else if (difference.compareTo(BigDecimal.ZERO) < 0) {
                trend = TrendDirection.DOWN;
            } else {
                trend = TrendDirection.STABLE;
            }

            // Check for abnormal increase
            boolean abnormalIncrease = percentage.compareTo(THRESHOLD_PERCENTAGE) > 0;

            // Set fields
            charge.setTrend(trend);
            charge.setTrendPercentage(percentage);
            charge.setPreviousAmount(previousAmount);
            charge.setAbnormalIncrease(abnormalIncrease);
        } else {
            // No previous charge found
            charge.setTrend(null);
            charge.setTrendPercentage(null);
            charge.setPreviousAmount(null);
            charge.setAbnormalIncrease(false);
        }
    }

    // ==================== DTO Mapping ====================

    private FixedChargeResponse toFixedChargeResponse(FixedCharge charge) {
        // For weekly personnel charges, calculate accumulated amount from all week salaries for the month
        BigDecimal amount = charge.getAmount();
        if (charge.getCategory() == ChargeCategory.PERSONNEL && charge.getPeriod() == ChargePeriod.WEEK && charge.getMonthKey() != null) {
            amount = calculateAccumulatedAmountForMonth(charge, charge.getMonthKey());
        }
        
        return FixedChargeResponse.builder()
                .id(charge.getId())
                .category(charge.getCategory())
                .name(charge.getName())
                .amount(amount)
                .period(charge.getPeriod())
                .monthKey(charge.getMonthKey())
                .weekKey(charge.getWeekKey())
                .trend(charge.getTrend())
                .trendPercentage(charge.getTrendPercentage())
                .abnormalIncrease(charge.getAbnormalIncrease())
                .createdAt(charge.getCreatedAt())
                .updatedAt(charge.getUpdatedAt())
                .build();
    }
    
    /**
     * Calculate accumulated amount for weekly charges by summing all week salaries for the month
     */
    private BigDecimal calculateAccumulatedAmountForMonth(FixedCharge charge, String monthKey) {
        BigDecimal total = BigDecimal.ZERO;
        
        if (charge.getEmployees() != null) {
            for (PersonnelEmployee employee : charge.getEmployees()) {
                if (employee.getWeekSalariesList() != null) {
                    // Sum all week salaries that belong to this month
                    BigDecimal employeeTotal = employee.getWeekSalariesList().stream()
                            .filter(ws -> ws.getMonthKey() != null && ws.getMonthKey().equals(monthKey))
                            .map(PersonnelWeekSalary::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    total = total.add(employeeTotal);
                }
            }
        }
        
        return total.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private FixedChargeDetailResponse toFixedChargeDetailResponse(FixedCharge charge, String month, Long storeId) {
        // Use requested month or charge's month key
        String monthKey = month != null ? month : charge.getMonthKey();
        
        // For weekly charges, calculate accumulated amount for the month
        BigDecimal amount = charge.getAmount();
        if (charge.getCategory() == ChargeCategory.PERSONNEL && charge.getPeriod() == ChargePeriod.WEEK && monthKey != null) {
            amount = calculateAccumulatedAmountForMonth(charge, monthKey);
        }
        
        FixedChargeDetailResponse response = FixedChargeDetailResponse.builder()
                .id(charge.getId())
                .category(charge.getCategory())
                .name(charge.getName())
                .amount(amount)
                .period(charge.getPeriod())
                .monthKey(charge.getMonthKey())
                .weekKey(charge.getWeekKey())
                .trend(charge.getTrend())
                .trendPercentage(charge.getTrendPercentage())
                .abnormalIncrease(charge.getAbnormalIncrease())
                .previousAmount(charge.getPreviousAmount())
                .notes(charge.getNotes())
                .createdAt(charge.getCreatedAt())
                .updatedAt(charge.getUpdatedAt())
                .build();

        // Build personnel data if category is PERSONNEL
        if (charge.getCategory() == ChargeCategory.PERSONNEL && charge.getEmployees() != null) {
            Map<EmployeeType, List<PersonnelEmployeeDTO>> groupedByType = charge.getEmployees().stream()
                    .map(emp -> toPersonnelEmployeeDTOFilteredByMonth(emp, monthKey))
                    .collect(Collectors.groupingBy(emp -> emp.getType() != null ? emp.getType() : EmployeeType.SERVER));

            List<PersonnelDataDTO> personnelData = new ArrayList<>();
            for (Map.Entry<EmployeeType, List<PersonnelEmployeeDTO>> entry : groupedByType.entrySet()) {
                // Calculate total amount for the requested month
                BigDecimal totalAmount = BigDecimal.ZERO;
                if (monthKey != null) {
                    totalAmount = entry.getValue().stream()
                            .map(emp -> {
                                // Sum weekSalaries for the requested month
                                if (emp.getWeekSalaries() != null && !emp.getWeekSalaries().isEmpty()) {
                                    return emp.getWeekSalaries().entrySet().stream()
                                            .filter(e -> WeekCalculationUtils.getMonthKeyForWeek(e.getKey()).equals(monthKey))
                                            .map(Map.Entry::getValue)
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                                }
                                // Fallback to monthSalary if it belongs to the requested month
                                return emp.getMonthSalary() != null ? emp.getMonthSalary() : BigDecimal.ZERO;
                            })
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                } else {
                    // If no month specified, use salary field
                    totalAmount = entry.getValue().stream()
                            .map(emp -> emp.getSalary() != null ? emp.getSalary() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                }

                personnelData.add(PersonnelDataDTO.builder()
                        .type(entry.getKey())
                        .totalAmount(totalAmount)
                        .employees(entry.getValue())
                        .build());
            }
            response.setPersonnelData(personnelData);
        }

        // Build chart data (historical charges; for OTHER, filter by same name)
        String monthKeyForChart = month != null ? month : charge.getMonthKey();
        List<FixedCharge> historicalCharges;
        if (charge.getCategory() == ChargeCategory.OTHER && charge.getName() != null && !charge.getName().trim().isEmpty()) {
            historicalCharges = fixedChargeRepository.findHistoricalChargesWithName(
                    storeId,
                    charge.getCategory(),
                    charge.getPeriod(),
                    charge.getName().trim(),
                    monthKeyForChart
            );
        } else {
            historicalCharges = fixedChargeRepository.findHistoricalCharges(
                    storeId,
                    charge.getCategory(),
                    charge.getPeriod(),
                    monthKeyForChart
            );
        }
        List<ChartDataDTO> chartData = historicalCharges.stream()
                .map(hc -> ChartDataDTO.builder()
                        .period(formatMonthKey(hc.getMonthKey()))
                        .amount(hc.getAmount())
                        .build())
                .collect(Collectors.toList());
        response.setChartData(chartData);

        return response;
    }

    private PersonnelEmployeeDTO toPersonnelEmployeeDTO(PersonnelEmployee emp) {
        // Use weekSalaries from the entity (computed from weekSalariesList via @PostLoad)
        // This is already filtered correctly based on PersonnelWeekSalary records
        return PersonnelEmployeeDTO.builder()
                .id(emp.getId())
                .name(emp.getName())
                .type(emp.getType())
                .position(emp.getPosition())
                .startDate(emp.getStartDate())
                .salary(emp.getSalary())
                .hours(emp.getHours())
                .salaryByPeriod(emp.getSalaryByPeriod())
                .monthSalary(emp.getMonthSalary())
                .weekSalary(emp.getWeekSalary())
                .weekSalaries(emp.getWeekSalaries())
                .daysLeftSalary(emp.getDaysLeftSalary())
                .build();
    }
    
    /**
     * Convert PersonnelEmployee to DTO with weekSalaries filtered by month
     * Only includes weeks that belong to the requested month
     */
    private PersonnelEmployeeDTO toPersonnelEmployeeDTOFilteredByMonth(PersonnelEmployee emp, String monthKey) {
        Map<String, BigDecimal> filteredWeekSalaries = new HashMap<>();
        BigDecimal monthTotal = BigDecimal.ZERO;
        
        if (emp.getWeekSalariesList() != null && monthKey != null) {
            // Filter week salaries by month
            for (PersonnelWeekSalary weekSalary : emp.getWeekSalariesList()) {
                if (weekSalary.getMonthKey() != null && weekSalary.getMonthKey().equals(monthKey)) {
                    filteredWeekSalaries.put(weekSalary.getWeekKey(), weekSalary.getAmount());
                    monthTotal = monthTotal.add(weekSalary.getAmount());
                }
            }
        } else if (emp.getWeekSalaries() != null && monthKey != null) {
            // Fallback to transient map if weekSalariesList is not available
            for (Map.Entry<String, BigDecimal> entry : emp.getWeekSalaries().entrySet()) {
                String weekMonthKey = WeekCalculationUtils.getMonthKeyForWeek(entry.getKey());
                if (weekMonthKey.equals(monthKey)) {
                    filteredWeekSalaries.put(entry.getKey(), entry.getValue());
                    monthTotal = monthTotal.add(entry.getValue());
                }
            }
        } else {
            // If no month filter, use all week salaries
            filteredWeekSalaries = emp.getWeekSalaries() != null ? new HashMap<>(emp.getWeekSalaries()) : new HashMap<>();
            monthTotal = emp.getMonthSalary() != null ? emp.getMonthSalary() : BigDecimal.ZERO;
        }
        
        return PersonnelEmployeeDTO.builder()
                .id(emp.getId())
                .name(emp.getName())
                .type(emp.getType())
                .position(emp.getPosition())
                .startDate(emp.getStartDate())
                .salary(emp.getSalary())
                .hours(emp.getHours())
                .salaryByPeriod(emp.getSalaryByPeriod())
                .monthSalary(monthTotal)
                .weekSalary(emp.getWeekSalary())
                .weekSalaries(filteredWeekSalaries)
                .daysLeftSalary(emp.getDaysLeftSalary())
                .build();
    }

    private VariableChargeResponse toVariableChargeResponse(VariableCharge charge) {
        return VariableChargeResponse.builder()
                .id(charge.getId())
                .name(charge.getName())
                .amount(charge.getAmount())
                .date(charge.getDate())
                .category(charge.getCategory())
                .supplier(charge.getSupplier())
                .notes(charge.getNotes())
                .purchaseOrderUrl(charge.getPurchaseOrderUrl())
                .createdAt(charge.getCreatedAt())
                .updatedAt(charge.getUpdatedAt())
                .build();
    }

    // ==================== Helper Methods ====================

    private void validateFixedChargeRequest(FixedChargeCreateRequest request) {
        if (request.getPeriod() == ChargePeriod.WEEK && (request.getWeekKey() == null || request.getWeekKey().isEmpty())) {
            throw new RuntimeException("Week key is required when period is WEEK");
        }

        // Validate week key format (must be Monday date: YYYY-MM-DD)
        if (request.getPeriod() == ChargePeriod.WEEK && request.getWeekKey() != null) {
            if (!WeekCalculationUtils.isValidWeekKey(request.getWeekKey())) {
                throw new RuntimeException("Invalid week key format. Week key must be a Monday date in format YYYY-MM-DD");
            }
            
            // Validate that week overlaps with month key if provided
            if (request.getMonthKey() != null && !WeekCalculationUtils.weekOverlapsWithMonth(request.getWeekKey(), request.getMonthKey())) {
                throw new RuntimeException("Week key does not overlap with the provided month key");
            }
        }

        if (request.getCategory() == ChargeCategory.OTHER) {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                throw new RuntimeException("Name is required for custom (Other) fixed charges");
            }
        }

        if (request.getCategory() != ChargeCategory.PERSONNEL && 
            (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new RuntimeException("Amount is required for non-personnel charges");
        }

        // Validate utilities and OTHER must be MONTH period
        if (request.getCategory() != ChargeCategory.PERSONNEL && request.getPeriod() != ChargePeriod.MONTH) {
            throw new RuntimeException("Non-personnel fixed charges (water, electricity, wifi, other) must use MONTH period");
        }
    }

    private String formatMonthKey(String monthKey) {
        // Format "YYYY-MM" to "MMM YYYY" (e.g., "2024-03" -> "Mar 2024")
        if (monthKey == null || monthKey.length() != 7) {
            return monthKey;
        }
        try {
            YearMonth yearMonth = YearMonth.parse(monthKey);
            return yearMonth.format(DateTimeFormatter.ofPattern("MMM yyyy"));
        } catch (Exception e) {
            return monthKey;
        }
    }

    // ==================== User preferences (personnel last period) ====================

    /**
     * Get the authenticated user's last used period for personnel fixed charges (week or month).
     * Used to pre-select period when creating a new personnel charge on another month.
     */
    public Optional<String> getPersonnelChargeLastPeriod() {
        String userId = KeycloakTokenUtils.getUserId();
        if (userId == null) {
            return Optional.empty();
        }
        return userPreferenceRepository.findByUserIdAndPreferenceKey(userId, PREFERENCE_PERSONNEL_LAST_PERIOD)
                .map(UserPreference::getPreferenceValue);
    }

    /**
     * Save the authenticated user's last used period for personnel fixed charges.
     */
    @Transactional
    public void setPersonnelChargeLastPeriod(String period) {
        if (period == null || (!period.equals("week") && !period.equals("month"))) {
            return;
        }
        String userId = KeycloakTokenUtils.getUserId();
        if (userId == null) {
            return;
        }
        UserPreference pref = userPreferenceRepository.findByUserIdAndPreferenceKey(userId, PREFERENCE_PERSONNEL_LAST_PERIOD)
                .orElse(UserPreference.builder()
                        .userId(userId)
                        .preferenceKey(PREFERENCE_PERSONNEL_LAST_PERIOD)
                        .build());
        pref.setPreferenceValue(period);
        pref.setUpdatedAt(OffsetDateTime.now());
        userPreferenceRepository.save(pref);
    }
}
