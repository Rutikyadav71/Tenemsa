import type { AxiosResponse } from 'axios';
import axiosInstance from '../../../shared/api/axiosInstance';
import type { EmployeeFormInput, Leave, PageResponse, User } from '../../../shared/types/models';

const EMP_API_URL = '/api/employees';

/**
 * NOTE:
 * Although we are using `emp_id` as the actual DB primary key,
 * the backend exposes it as `id` in the REST API endpoints for simplicity.
 */

/**
 * GET /api/employees now returns Spring Data's Page<T> shape
 * ({content, totalElements, ...}), not a plain array — added in Phase 3 for
 * real pagination (see docs/roadmap.md). The unwrap happens here, once,
 * rather than in every component that lists employees: existing callers
 * (EmployeeList, UpdateEmployee) still just read res.data as a User[] and
 * don't need to change. Defaults to a large page size so the current
 * client-side search/filtering UX keeps working unmodified; a real
 * server-side-paginated UI (using totalElements/totalPages) is a follow-up,
 * not bundled into this fix.
 */
export const getEmployees = (): Promise<AxiosResponse<User[]>> =>
  axiosInstance.get<PageResponse<User>>(`${EMP_API_URL}?size=1000`)
    .then(res => ({ ...res, data: res.data.content }));

export const addEmployee = (emp: EmployeeFormInput): Promise<AxiosResponse<User>> =>
  axiosInstance.post(EMP_API_URL, emp);

export const updateEmployee = (
  empId: number | string,
  emp: Partial<EmployeeFormInput>
): Promise<AxiosResponse<User>> =>
  axiosInstance.put(`${EMP_API_URL}/${empId}`, emp);

export const deleteEmployee = (empId: number | string): Promise<AxiosResponse<void>> =>
  axiosInstance.delete(`${EMP_API_URL}/${empId}`);

export const searchEmployees = (keyword: string): Promise<AxiosResponse<User[]>> => {
  return axiosInstance.get(`${EMP_API_URL}/search?keyword=${encodeURIComponent(keyword)}`);
};

export const deleteMultipleEmployees = (empIds: number[]): Promise<AxiosResponse<void>> => {
  return axiosInstance.post(`${EMP_API_URL}/delete-multiple`, empIds);
};

const LEAVE_API_URL = "/api/leaves";

export const getLeaveById = (leaveId: number | string): Promise<AxiosResponse<Leave>> => {
  return axiosInstance.get(`${LEAVE_API_URL}/${leaveId}`);
};

export const getLeavePrediction = (leaveId: number | string): Promise<AxiosResponse<unknown>> => {
  return axiosInstance.get(`${LEAVE_API_URL}/admin/${leaveId}/prediction`);
};
