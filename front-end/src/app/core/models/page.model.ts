/** One page of results from the API (PageResponse in the backend). `page` starts at 0. */
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}
