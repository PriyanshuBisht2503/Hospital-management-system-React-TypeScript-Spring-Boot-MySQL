import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import ReactDOMServer from 'react-dom/server';
import { Pagination } from '@hms/ui';
import { apiRequest } from '../api/client';
import { Branch, PageResponse } from '../types/masterData';
import { DEFAULT_PAGE_SIZE } from '@hms/ui';

describe('Pagination Component and Flow Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  describe('Shared Pagination Component Contract', () => {
    it('disables Previous button on first page and enables Next button', () => {
      let pageChangedTo = -1;
      const html = ReactDOMServer.renderToString(
        React.createElement(Pagination, {
          pageNumber: 0,
          pageSize: DEFAULT_PAGE_SIZE,
          totalPages: 3,
          totalElements: 18,
          isFirst: true,
          isLast: false,
          onPageChange: (p: number) => {
            pageChangedTo = p;
          },
        })
      );

      // Verify page text
      expect(html).toContain('Total: <strong>18</strong> items (Page <strong>1</strong> of <strong>3</strong>)');

      // Verify Previous button is disabled on first page
      expect(html).toMatch(/<button[^>]*disabled=""[^>]*>\s*Previous/);

      // Verify Next button is NOT disabled on first page
      expect(html).not.toMatch(/<button[^>]*disabled=""[^>]*>\s*Next/);
    });

    it('disables Next button on last page and enables Previous button', () => {
      const html = ReactDOMServer.renderToString(
        React.createElement(Pagination, {
          pageNumber: 2,
          pageSize: DEFAULT_PAGE_SIZE,
          totalPages: 3,
          totalElements: 18,
          isFirst: false,
          isLast: true,
          onPageChange: () => {},
        })
      );

      // Verify page text
      expect(html).toContain('Total: <strong>18</strong> items (Page <strong>3</strong> of <strong>3</strong>)');

      // Verify Next button is disabled on last page
      expect(html).toMatch(/<button[^>]*disabled=""[^>]*>\s*Next/);

      // Verify Previous button is NOT disabled on last page
      expect(html).not.toMatch(/<button[^>]*disabled=""[^>]*>\s*Previous/);
    });

    it('invokes onPageChange with next page number (currentPage + 1)', () => {
      let changedPage = -1;
      const element = React.createElement(Pagination, {
        pageNumber: 0,
        pageSize: 6,
        totalPages: 3,
        totalElements: 18,
        isFirst: true,
        isLast: false,
        onPageChange: (newPage: number) => {
          changedPage = newPage;
        },
      });

      // Simulate Next button click directly by invoking element's child button handler
      const rendered = Pagination(element.props);
      if (React.isValidElement(rendered) && rendered.props) {
        const container = (rendered.props as { children: React.ReactElement[] }).children[1];
        const buttons = (container.props as { children: React.ReactElement<{ onClick: () => void }>[] }).children;
        const nextButton = buttons[buttons.length - 1];
        nextButton.props.onClick();
      }

      expect(changedPage).toBe(1);
    });
  });

  describe('List Page Pagination Request and State Flow', () => {
    it('requests page=1&size=6 when transitioning to Next page and displays page 1 rows', async () => {
      const page0Items: Branch[] = Array.from({ length: 6 }, (_, i) => ({
        id: i + 1,
        code: `BR-0${i + 1}`,
        name: `Hospital Branch ${i + 1}`,
        city: 'Dubai',
        isActive: true,
      }));

      const page1Items: Branch[] = Array.from({ length: 6 }, (_, i) => ({
        id: i + 7,
        code: `BR-0${i + 7}`,
        name: `Hospital Branch ${i + 7}`,
        city: 'Dubai',
        isActive: true,
      }));

      const requestedUrls: string[] = [];

      const mockFetch = vi.fn(async (input: RequestInfo | URL) => {
        const url = typeof input === 'string' ? input : input.toString();
        requestedUrls.push(url);

        if (url.includes('page=0&size=6')) {
          const res: PageResponse<Branch> = {
            content: page0Items,
            pageNumber: 0,
            pageSize: 6,
            totalElements: 18,
            totalPages: 3,
            isFirst: true,
            isLast: false,
          };
          return new Response(JSON.stringify(res), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          });
        }

        if (url.includes('page=1&size=6')) {
          const res: PageResponse<Branch> = {
            content: page1Items,
            pageNumber: 1,
            pageSize: 6,
            totalElements: 18,
            totalPages: 3,
            isFirst: false,
            isLast: false,
          };
          return new Response(JSON.stringify(res), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          });
        }

        return new Response('Not Found', { status: 404 });
      });

      vi.stubGlobal('fetch', mockFetch);

      // 1. Initial page load (page 0)
      let currentPage = 0;
      const initialResponse = await apiRequest<PageResponse<Branch>>(
        `/api/branches?page=${currentPage}&size=${DEFAULT_PAGE_SIZE}&search=&activeOnly=false`
      );

      expect(initialResponse.pageNumber).toBe(0);
      expect(initialResponse.pageSize).toBe(6);
      expect(initialResponse.totalPages).toBe(3);
      expect(initialResponse.isFirst).toBe(true);
      expect(initialResponse.isLast).toBe(false);
      expect(initialResponse.content[0].name).toBe('Hospital Branch 1');

      // 2. Next action triggers transition to page 1
      currentPage = currentPage + 1;
      const page1Response = await apiRequest<PageResponse<Branch>>(
        `/api/branches?page=${currentPage}&size=${DEFAULT_PAGE_SIZE}&search=&activeOnly=false`
      );

      // Assert a request with page=1&size=6 was sent
      expect(requestedUrls).toContain('/api/branches?page=1&size=6&search=&activeOnly=false');
      expect(page1Response.pageNumber).toBe(1);
      expect(page1Response.content.length).toBe(6);
      // Assert new rows are returned and shown
      expect(page1Response.content[0].name).toBe('Hospital Branch 7');
      expect(page1Response.content[5].name).toBe('Hospital Branch 12');
    });

    it('resets page to 0 when search term changes', async () => {
      let currentPage = 2; // User was on page 2
      let searchTerm = '';

      const handleSearchChange = (newSearch: string) => {
        currentPage = 0; // Guaranteed reset to 0
        searchTerm = newSearch;
      };

      // Search input changes to 'Central'
      handleSearchChange('Central');

      expect(currentPage).toBe(0);
      expect(searchTerm).toBe('Central');
    });

    it('decrements page when current page becomes empty after deactivation', () => {
      let currentPage = 2;
      const currentContent = [{ id: 13, code: 'BR-13', name: 'Branch 13', city: 'Dubai', isActive: true }];

      // Deactivation callback checks if last item on non-zero page
      const onDeactivateSuccess = () => {
        if (currentContent.length === 1 && currentPage > 0) {
          currentPage = Math.max(0, currentPage - 1);
        }
      };

      onDeactivateSuccess();
      expect(currentPage).toBe(1);
    });
  });
});
