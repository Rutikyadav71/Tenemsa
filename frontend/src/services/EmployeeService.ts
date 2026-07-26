import type { AxiosResponse } from 'axios';
import axiosInstance from './axiosInstance';
import type { EmployeeFormInput, Leave, User } from '../types/models';

const EMP_API_URL = '/api/employees';

/**
 * NOTE:
 * Although we are using `emp_id` as the actual DB primary key,
 * the backend exposes it as `id` in the REST API endpoints for simplicity.
 */

export const getEmployees = (): Promise<AxiosResponse<User[]>> =>
  axiosInstance.get(EMP_API_URL);

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
