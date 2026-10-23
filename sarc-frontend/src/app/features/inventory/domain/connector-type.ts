import {
  ConnectorTypeResponse,
  CreateConnectorTypeRequest,
  UpdateConnectorTypeRequest,
} from '../../../core/api/generated';

export type ConnectorType = ConnectorTypeResponse;
export type CreateConnectorTypeInput = CreateConnectorTypeRequest;
export type UpdateConnectorTypeInput = UpdateConnectorTypeRequest;

/** Los tipos no se eliminan (pueden estar en historicos); solo se activan o inactivan. */
export type ConnectorTypeStatus = Exclude<ConnectorType['status'], 'DELETED'>;
