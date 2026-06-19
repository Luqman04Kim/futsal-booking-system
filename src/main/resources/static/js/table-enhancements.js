/**
 * FOOTBALLHUB — Table Enhancements
 * #5  Empty state screens
 * #6  Loading skeleton / page-ready fade-in
 * #14 Export to CSV & Print/PDF
 * #15 Live search / filter
 */

document.addEventListener('DOMContentLoaded', function () {

    // ===================================================
    // #6  PAGE LOADING — fade in content when ready
    // ===================================================
    const pageLoader = document.getElementById('fh-page-loader');
    if (pageLoader) {
        pageLoader.style.opacity = '0';
        pageLoader.style.transition = 'opacity 0.4s ease';
        setTimeout(() => { pageLoader.style.display = 'none'; }, 500);
    }
    // Fade in main content
    const mainContent = document.querySelector('.content');
    if (mainContent) {
        mainContent.style.opacity = '0';
        mainContent.style.transform = 'translateY(8px)';
        mainContent.style.transition = 'opacity 0.5s ease, transform 0.5s ease';
        setTimeout(() => {
            mainContent.style.opacity = '1';
            mainContent.style.transform = 'translateY(0)';
        }, 100);
    }

    // ===================================================
    // #5  EMPTY STATE — upgrade plain "no data" <tr>s
    // ===================================================
    document.querySelectorAll('tbody').forEach(tbody => {
        const emptyRows = tbody.querySelectorAll('tr[class*="empty"], tr td[colspan]');
        emptyRows.forEach(el => {
            const td = el.tagName === 'TD' ? el : el.querySelector('td[colspan]');
            if (!td) return;
            const text = td.textContent.trim().toLowerCase();
            if (text.length < 5 || text.length > 120) return;
            td.innerHTML = `
                <div style="padding:40px 20px;text-align:center;">
                    <div style="font-size:3rem;margin-bottom:12px;opacity:0.3;">📋</div>
                    <div style="font-weight:600;color:#64748b;font-size:0.95rem;margin-bottom:6px;">No Records Found</div>
                    <div style="color:#94a3b8;font-size:0.82rem;">${td.textContent.trim() || 'There is nothing to display for this period.'}</div>
                </div>`;
        });
    });

    // ===================================================
    // #15  LIVE SEARCH — works on any table with data-searchable
    // ===================================================
    document.querySelectorAll('input[data-search-table]').forEach(searchInput => {
        const tableId = searchInput.getAttribute('data-search-table');
        const table = document.getElementById(tableId);
        if (!table) return;
        const tbody = table.querySelector('tbody');
        if (!tbody) return;

        searchInput.addEventListener('input', function () {
            const query = this.value.toLowerCase().trim();
            let visibleCount = 0;

            tbody.querySelectorAll('tr').forEach(row => {
                // Skip empty-state rows
                if (row.querySelector('td[colspan]') && row.querySelectorAll('td').length === 1) return;
                const text = row.textContent.toLowerCase();
                const match = !query || text.includes(query);
                row.style.display = match ? '' : 'none';
                if (match) visibleCount++;
            });

            // Show/hide "no results" message
            let noResults = table.parentElement.querySelector('.fh-no-results');
            if (!noResults) {
                noResults = document.createElement('div');
                noResults.className = 'fh-no-results';
                noResults.style.cssText = 'padding:30px;text-align:center;color:#94a3b8;font-size:0.9rem;display:none;';
                noResults.innerHTML = '<i class="fas fa-search" style="font-size:1.8rem;opacity:0.3;display:block;margin-bottom:8px;"></i>No results match "<strong class="search-term"></strong>"';
                table.after(noResults);
            }
            noResults.querySelector('.search-term').textContent = query;
            noResults.style.display = (visibleCount === 0 && query) ? 'block' : 'none';
        });
    });

    // ===================================================
    // #14  EXPORT CSV
    // ===================================================
    window.exportTableCSV = function (tableId, filename) {
        const table = document.getElementById(tableId);
        if (!table) return;
        const rows = table.querySelectorAll('tr');
        const csv = [];

        rows.forEach(row => {
            const cells = row.querySelectorAll('th, td');
            const rowData = [];
            cells.forEach(cell => {
                // Skip action column cells (buttons)
                if (cell.querySelector('button, a.btn')) { rowData.push(''); return; }
                let text = cell.innerText.replace(/\n/g, ' ').replace(/,/g, ';').trim();
                rowData.push(`"${text}"`);
            });
            csv.push(rowData.join(','));
        });

        const blob = new Blob([csv.join('\n')], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename || 'export.csv';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    };

    // ===================================================
    // #14  PRINT / PDF
    // ===================================================
    window.printTable = function (tableId, title) {
        const table = document.getElementById(tableId);
        if (!table) { window.print(); return; }

        const printWin = window.open('', '_blank', 'width=900,height=700');
        printWin.document.write(`<!DOCTYPE html>
<html><head>
<title>${title || 'FOOTBALLHUB Report'}</title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
<style>
  body { font-family: Inter, sans-serif; padding: 30px; font-size: 12px; }
  h2 { color: #004d98; }
  table { width: 100%; border-collapse: collapse; }
  th { background: #0f172a; color: white; padding: 8px; }
  td { padding: 7px 8px; border-bottom: 1px solid #e2e8f0; }
  tr:nth-child(even) { background: #f8fafc; }
  .no-print { display: none !important; }
  @media print { .no-print { display: none !important; } }
</style>
</head><body>
<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;">
  <div>
    <h2 style="margin:0;">⚽ FOOTBALLHUB</h2>
    <div style="color:#64748b;font-size:11px;">${title || 'Report'} — Generated ${new Date().toLocaleString('en-MY')}</div>
  </div>
</div>
${table.outerHTML}
<script>window.onload = () => { window.print(); window.close(); }<\/script>
</body></html>`);
        printWin.document.close();
    };

});
