# Escenarios de quickstart (T125)

Cada escenario se asoció a pruebas existentes por el nombre del método, que pasan en `final-gates.md`. No es una ejecución manual de los 48 recorridos. Resultado: 42 cubiertos, 6 parciales.

| # | Estado | Evidencia |
|---|---|---|
| 1 | Auto | `UptimeFormulaTest`, `registersPendingMeasurementWithComputedUptime`; navegador |
| 2 | Auto | `twoConnectorsOfOneStationStayIndependent` |
| 3 | Parcial | Navegador sin arrastre; tiempo pendiente (T124) |
| 4 | Auto | `eightySixThousandMinutesInOneWeekExceedsDuration`, `percentBeyondTechnicalRangeCreatesNothing` |
| 5 | Auto | `rollingWindowToleratesOneMinute`, `validatableObservationNeedsJustification`, `notValidatableObservationMustBeCorrected` |
| 6 | Auto | `identicalReturnsExistingWithoutWriting`, `differentValuesConflictWithIdAndVersion` |
| 7 | Auto | `effectiveStaysUntilCorrectionIsValidated` |
| 8 | Auto | `exclusionIsReversibleAndNeverDeletes` |
| 9 | Auto | `twoCorrectionsOnlyOneWins`, `twoValidationsOnlyOneWins` |
| 10 | Auto | `batchSkipsObservedStaleAndMissing`, `batchReportsStaleAndNotPending` |
| 11 | Auto | `alignedWindowWithoutConfirmationStaysProvisional` |
| 12 | Auto | `withoutOfficialOpensProvisionalAndWarns` |
| 13 | Auto | `combinedFiltersNarrowBlocks`, `provisionalBlockKeepsPreferenceBesideOfficial`; navegador |
| 14 | Auto | `overlappingWindowsAreNotDoubleCounted` |
| 15 | Auto | `noDataGivesNullNeverZero`, `reportsHoursAndZeroPercentIsIncluded`, `noDataWithoutMeasurementAndPendingStayInDenominator` |
| 16 | Auto | `tieOnPresentedValueSharesPositionAndOrdersByName` |
| 17 | Auto | `readsTheFourStatesInAnyOrder`, `ambiguousBlockNamesWhatIsMissing` |
| 18 | Parcial | `readsOnlyTheFirstSheet` en parser; carga de archivo desactivada hasta DP-04b |
| 19 | Auto | `reimportingIdenticalWritesNothing` |
| 20 | Auto | `ambiguousIdentityStaysPendingWithoutEquipment`; navegador |
| 21 | Auto | `UptimeHistoryContinuityTest` |
| 22 | Auto | `usesCurrentNameAndLabelsEquipmentAverage`, `correctionDoesNotChangeAnIssuedReport`, `detailDistinguishesFactLabelFromCurrentName` |
| 23 | Auto | `officialAndProvisionalFiguresMatchTheDashboard` |
| 24 | Auto | `UptimeSecurityTest`, `UptimeReadProjectionTest`; `GERENTE` en navegador |
| 25 | Auto | `consumption-comparison.md` |
| 26 | Auto | `approvedAliasResolvesLaterImportsWithEquivalentWriting` |
| 27 | Auto | `reassigningAnAliasLeavesMeasurementsAlone` |
| 28 | Auto | `rollingWindowOfIrregularDurationIsNotValidatable` |
| 29 | Auto | `prefersSevenDaysWithoutOfficial` |
| 30 | Auto | `rangeLimitIs366Days`, `rejectsRangeOver366Days` |
| 31 | Parcial | `withoutOfficialOpensProvisionalAndWarns`; ausencia de tendencia sin prueba propia identificada |
| 32 | Auto | `inactiveEntersOnlyWithMeasurement`, `inactiveEquipmentWithoutMeasurementDoesNotAppear` |
| 33 | Auto | `unattributedTotalIgnoresConnectorFilters` |
| 34 | Auto | `rangeCoversOneNominalDurationBeforeLatest` |
| 35 | Auto | `officialCountsConnectorWeekPairs` |
| 36 | Auto | `excludedStaysInDenominatorWithoutCovering`, `excludedDisappearsForConsultation` |
| 37 | Auto | `textDurationIsCaptureErrorWithoutMeasurement`, `observedKeepsWhatWasTypedAndHasNoPercent` |
| 38 | Auto | `retiredConnectorWithoutMeasurementIsListedAsRetired` |
| 39 | Auto | `alignedWindowBecomesExactWeekOnlyByCorrection` |
| 40 | Auto | `durationOutsideDeclaredWindowStaysObserved` |
| 41 | Auto | `exclusionsCarryOnlyConnectorAndState` |
| 42 | Parcial | `refreshFailed` en `DashboardFacade`; aviso no forzado en navegador |
| 43 | Auto | `correctionKeepsFactLabelAfterRename` |
| 44 | Auto | `provisionalBlockKeepsPreferenceBesideOfficial` |
| 45 | Parcial | `officialRangeMustStartOnMonday`, `officialCountsConnectorWeekPairs`; semana abierta sin prueba propia identificada |
| 46 | Auto | `fractionalValueIsObservedNotRounded`, `observedKeepsWhatWasTypedAndHasNoPercent` |
| 47 | Auto | `twoDifferentNonIntegerOriginalsConflict` |
| 48 | Parcial | `confirmationCreatesPendingMeasurementWithSourceLabel`, `detailDistinguishesFactLabelFromCurrentName`; recorrido combinado sin ejecutar |
