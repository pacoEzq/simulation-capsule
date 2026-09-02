// Simcenter STAR-CCM+ Java Macro: TrimReportForAI.java
// ============================================
// Purpose: Reads a Simcenter STAR-CCM+ Summary Report HTML file and produces a trimmed
//          plain-text version optimized for AI/LLM consumption.
//
// Usage:   1. Open your simulation in Simcenter STAR-CCM+
//          2. Generate a Summary Report (File > Export > Summary Report)
//          3. Run this macro - it auto-detects the most recent .html report
//             in the simulation directory, or set REPORT_PATH_OVERRIDE below.
//          4. The trimmed output is saved as <original_name>_AI.txt
//
// What gets REMOVED (~90% token reduction):
//   - All HTML markup, CSS, table formatting
//   - Rendering Materials (colors, specular, roughness, sheen, etc.)
//   - 3D-CAD Models (geometry features, sketches, dimensions)
//   - Parts, Representations, Contacts (geometry metadata)
//   - Color Palettes, Screenplays, Layout/Solution Views
//   - Descriptions, Coordinate Systems, Parameterizations, Tables, Units
//   - Custom Trees, Idealizations, Data Set Functions, User Code
//   - Data Focus, Data Mappers, Summaries, Solution Histories
//   - All "Tags" rows (always empty [])
//   - All visual/display properties (Color, Opacity, Roughness, etc.)
//   - All empty "Error Message" rows
//   - All "Maximum Plot Samples" rows
//   - Tree-drawing characters (|, +-, `-) replaced by indentation
//
// What gets KEPT (physics-relevant for AI analysis):
//   - Session/Software/Hardware summary
//   - Continua (physics models, schemes, material properties)
//   - Regions (boundaries, boundary conditions, physics values)
//   - Operations (mesh operations, settings)
//   - Derived Parts (section planes, isosurfaces, thresholds)
//   - Monitors (tracked quantities, triggers)
//   - Reports (computed quantities, definitions)
//   - Solvers (URFs, linear solver settings, convergence)
//   - Stopping Criteria
//   - Motions, Reference Frames
//   - Automation (global parameters, user field functions)
//   - Solution (CPU time, time level, physical time)

import star.common.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

public class TrimReportForAI extends StarMacro {

    // ================================================================
    // CONFIGURATION
    // ================================================================
    
    // Set to a specific file path to override auto-detection.
    // Leave empty ("") to auto-detect the most recent .html in the sim dir.
    private static final String REPORT_PATH_OVERRIDE = "";
    
    // Top-level tree sections to KEEP (all others are removed)
    private static final Set<String> KEEP_SECTIONS = new HashSet<>(Arrays.asList(
        "Continua",
        "Regions",
	"Representations",  // Contains mesh statistics (cells, faces, vertices)
        "Operations",
        "Derived Parts",
        "Monitors",
        "Reports",
        "Solvers",
        "Stopping Criteria",
        "Automation",
        "Motions",
        "Reference Frames"
    ));
    
    // Property keys to STRIP even from kept sections
    private static final Set<String> SKIP_PROPS = new HashSet<>(Arrays.asList(
        // Always-empty or non-informative
        "Tags", "Error Message",
        // Visual / display properties
        "Color", "Diffuse Color", "Specular Color", "Specular Tint",
        "Specular", "Absorption", "Reflection", "Refraction",
        "Index of Refraction", "Reverse Surface Orientation",
        "Roughness", "Transmission Weight", "Transmission Roughness",
        "Subsurface Scattering Distance", "Sheen Tint", "Sheen",
        "Metallic", "Emission Intensity", "Emission Color",
        "Clearcoat IOR", "Clear Coat Weight", "Clear Coat Roughness",
        "Attenuation Distance", "Attenuation Color", "Solid Volume",
        "Set Tangency at all Edges", "Opacity",
        "Override Upstream Color", "IsAssignedColor", "Display Resolution",
        // Redundant per-monitor/report settings
        "Maximum Plot Samples",
        "Synchronize Function Name", "Inverse Distance Weight"
    ));

    // ================================================================
    // MAIN ENTRY POINT
    // ================================================================
    
    @Override
    public void execute() {
        Simulation sim = getActiveSimulation();
        
        // --- Locate report file ---
        String reportPath = REPORT_PATH_OVERRIDE;
        if (reportPath == null || reportPath.isEmpty()) {
            reportPath = findLatestReport(sim);
        }
        
        if (reportPath == null) {
            sim.println("[TrimReportForAI] ERROR: No HTML report found.");
            sim.println("  Generate one via File > Export > Summary Report,");
            sim.println("  or set REPORT_PATH_OVERRIDE in the macro.");
            return;
        }
        
        sim.println("[TrimReportForAI] Input:  " + reportPath);
        
        // --- Read ---
        List<String> htmlLines;
        try {
            htmlLines = readFile(reportPath);
        } catch (IOException e) {
            sim.println("[TrimReportForAI] ERROR reading file: " + e.getMessage());
            return;
        }
        
        long origSize = estimateBytes(htmlLines);
        sim.println("[TrimReportForAI] Read " + htmlLines.size() + " lines (" + origSize + " bytes)");
        
        // --- Trim ---
        String result = processReport(htmlLines);
        
        // --- Write ---
        String outPath = reportPath.replaceAll("(?i)\\.(html?)$", "") + "_AI.txt";
        try {
            writeFile(outPath, result);
        } catch (IOException e) {
            sim.println("[TrimReportForAI] ERROR writing file: " + e.getMessage());
            return;
        }
        
        long trimSize = result.length();
        double pct = (1.0 - (double) trimSize / origSize) * 100.0;
        sim.println("[TrimReportForAI] Output: " + outPath);
        sim.println(String.format("[TrimReportForAI] %,d bytes -> %,d bytes (%.1f%% reduction)",
                                  origSize, trimSize, pct));
    }
    
    // ================================================================
    // REPORT PROCESSING PIPELINE
    // ================================================================
    
    private String processReport(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        
        // 1. Find structural boundaries
        int simPropsLine = -1;
        int solutionLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            String ln = lines.get(i);
            if (ln.contains("<H3>Simulation Properties</H3>")) simPropsLine = i;
            if (ln.contains("<H3>Solution</H3>")) solutionLine = i;
        }
        
        // 2. Header block
        sb.append("============================================================\n");
        sb.append("STAR-CCM+ SIMULATION REPORT (AI-TRIMMED)\n");
        sb.append("============================================================\n");
        if (simPropsLine > 0) {
            sb.append(parseSimpleTable(lines, 0, simPropsLine));
        }
        
        // 3. Identify top-level tree sections
        Pattern secPat = Pattern.compile(
            "<tr[^>]*><td class=\"node\"><tt>(?:&nbsp;){2}\\+-(\\d+)&nbsp;</tt>(.*?)</td>"
        );
        
        List<int[]> ranges = new ArrayList<>();
        List<String> names = new ArrayList<>();
        
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = secPat.matcher(lines.get(i));
            if (m.find()) {
                names.add(stripHtml(m.group(2)));
                ranges.add(new int[]{i, -1});
            }
        }
        for (int i = 0; i < ranges.size(); i++) {
            ranges.get(i)[1] = (i + 1 < ranges.size())
                ? ranges.get(i + 1)[0]
                : ((solutionLine > 0) ? solutionLine : lines.size());
        }
        
        // 4. Emit kept sections
        sb.append("\n============================================================\n");
        sb.append("SIMULATION PROPERTIES\n");
        sb.append("============================================================\n");
        
        for (int i = 0; i < names.size(); i++) {
            if (!KEEP_SECTIONS.contains(names.get(i))) continue;
            sb.append("\n--- ").append(names.get(i)).append(" ---\n");
            sb.append(parseTreeTable(lines, ranges.get(i)[0], ranges.get(i)[1]));
        }
        
        // 5. Solution block
        if (solutionLine > 0) {
            sb.append("\n============================================================\n");
            sb.append("SOLUTION\n");
            sb.append("============================================================\n");
            sb.append(parseSimpleTable(lines, solutionLine, lines.size()));
        }
        
        return sb.toString();
    }
    
    // ================================================================
    // PARSER: Simple <td>key</td><td>value</td> tables (header/solution)
    // ================================================================
    
    private String parseSimpleTable(List<String> lines, int start, int end) {
        StringBuilder sb = new StringBuilder();
        Pattern boldPat = Pattern.compile("<b>([^<]+)</b>");
        Pattern tdPat = Pattern.compile("<td[^>]*>(.*?)</td>");
        
        // First join all lines into <tr> blocks, since key and value <td>
        // may be on separate lines in Summary Report HTML.
        List<String> trBlocks = new ArrayList<>();
        StringBuilder currentTr = null;
        
        for (int i = start; i < end && i < lines.size(); i++) {
            String ln = lines.get(i);
            if (ln.contains("<H2>") || ln.contains("<H3>")) {
                if (currentTr != null) { trBlocks.add(currentTr.toString()); currentTr = null; }
                trBlocks.add(ln);
            } else if (ln.contains("<tr")) {
                if (currentTr != null) trBlocks.add(currentTr.toString());
                currentTr = new StringBuilder(ln);
            } else if (currentTr != null) {
                currentTr.append(" ").append(ln);
            }
        }
        if (currentTr != null) trBlocks.add(currentTr.toString());
        
        // Now parse the joined blocks
        for (String block : trBlocks) {
            // H2/H3 titles
            if (block.contains("<H2>") || block.contains("<H3>")) {
                String t = stripHtml(block).trim();
                if (!t.isEmpty()) sb.append(t).append("\n");
                continue;
            }
            
            // Bold section headers (Session Summary, Software Summary, etc.)
            Matcher bm = boldPat.matcher(block);
            if (bm.find() && !block.contains("class=\"node\"")) {
                sb.append("\n[").append(bm.group(1).trim()).append("]\n");
                continue;
            }
            
            // Key-value td pairs
            List<String> tds = new ArrayList<>();
            Matcher tm = tdPat.matcher(block);
            while (tm.find()) {
                String content = tm.group(1).replaceAll("<br>", " | ").replaceAll("<[^>]*>", "").trim();
                tds.add(content);
            }
            if (tds.size() >= 2 && !tds.get(0).isEmpty() && !tds.get(1).isEmpty()) {
                String val = tds.get(1)
                    .replace("&nbsp;", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
                sb.append("  ").append(tds.get(0)).append(": ").append(val).append("\n");
            }
        }
        return sb.toString();
    }
    
    // ================================================================
    // PARSER: Nested tree-table (Simulation Properties)
    // ================================================================
    
    private String parseTreeTable(List<String> lines, int start, int end) {
        StringBuilder sb = new StringBuilder();
        
        Pattern ttPat = Pattern.compile("<tt>(.*?)</tt>");
        Pattern namePat = Pattern.compile("</tt>(?:<b>)?([^<]+)(?:</b>)?</td>");
        Pattern keyPat = Pattern.compile("(?:first-)?prop-key\">([^<]+)</td>");
        Pattern valPat = Pattern.compile("(?:first-)?prop-value\">(?:<IT>)?([^<]+)(?:</IT>)?</td>");
        
        for (int i = start; i < end && i < lines.size(); i++) {
            String ln = lines.get(i);
            
            // Only process tree rows
            if (!ln.contains("class=\"node\"") 
                && !ln.contains("prop-key") 
                && !ln.contains("first-prop-key")) {
                continue;
            }
            
            // Extract property key/value
            String pKey = null, pVal = null;
            Matcher km = keyPat.matcher(ln);
            if (km.find()) pKey = km.group(1).trim();
            Matcher vm = valPat.matcher(ln);
            if (vm.find()) pVal = vm.group(1).trim();
            
            // Filter out unwanted properties
            if (pKey != null && SKIP_PROPS.contains(pKey)) continue;
            
            // Filter out empty values
            if (pVal != null && pVal.replace("&nbsp;", "").trim().isEmpty()) {
                pVal = null;
            }
            
            // Extract depth and node name
            String nodeName = null;
            int depth = 0;
            Matcher ttm = ttPat.matcher(ln);
            if (ttm.find()) {
                depth = computeDepth(ttm.group(1));
                Matcher nm = namePat.matcher(ln);
                if (nm.find()) nodeName = nm.group(1).trim();
            }
            
            // Build output
            String indent = spaces(depth * 2);
            if (nodeName != null) {
                sb.append(indent).append(nodeName).append("\n");
                if (pKey != null && pVal != null) {
                    sb.append(indent).append("  ").append(pKey).append(": ").append(pVal).append("\n");
                }
            } else if (pKey != null && pVal != null) {
                sb.append(indent).append("  ").append(pKey).append(": ").append(pVal).append("\n");
            }
        }
        return sb.toString();
    }
    
    // ================================================================
    // UTILITIES
    // ================================================================
    
    /** Compute tree depth from <tt> content ("|" = parent level, "+-" or "`-" = +1). */
    private int computeDepth(String ttContent) {
        String s = ttContent.replace("&nbsp;", " ");
        int d = 0;
        for (char c : s.toCharArray()) {
            if (c == '|') d++;
        }
        if (s.contains("+-") || s.contains("`-")) d++;
        return d;
    }
    
    /** Strip all HTML tags from a string. */
    private String stripHtml(String s) {
        return s.replaceAll("<[^>]*>", "");
    }
    
    /** Create a string of n space characters. */
    private String spaces(int n) {
        char[] arr = new char[n];
        Arrays.fill(arr, ' ');
        return new String(arr);
    }
    
    /** Find the most recent .html file in the simulation directory. */
    private String findLatestReport(Simulation sim) {
        String sessionDir = sim.getSessionDir();
        File dir = new File(sessionDir);
        
        if (!dir.isDirectory()) {
            // Fallback: try parent of the .sim file path
            try {
                String simName = sim.getPresentationName();
                File simFile = new File(sim.getSessionDir(), simName + ".sim");
                if (simFile.getParentFile() != null) {
                    dir = simFile.getParentFile();
                }
            } catch (Exception e) {
                return null;
            }
        }
        
        File[] htmlFiles = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File d, String name) {
                String lower = name.toLowerCase();
                return (lower.endsWith(".html") || lower.endsWith(".htm"))
                    && !lower.contains("_ai.");
            }
        });
        
        if (htmlFiles == null || htmlFiles.length == 0) return null;
        
        File latest = htmlFiles[0];
        for (File f : htmlFiles) {
            if (f.lastModified() > latest.lastModified()) latest = f;
        }
        return latest.getAbsolutePath();
    }
    
    /** Read all lines from a text file (UTF-8). */
    private List<String> readFile(String path) throws IOException {
        List<String> result = new ArrayList<>();
        BufferedReader br = new BufferedReader(
            new InputStreamReader(new FileInputStream(path), "UTF-8"));
        String line;
        while ((line = br.readLine()) != null) result.add(line);
        br.close();
        return result;
    }
    
    /** Write a string to a file (UTF-8). */
    private void writeFile(String path, String content) throws IOException {
        BufferedWriter bw = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(path), "UTF-8"));
        bw.write(content);
        bw.close();
    }
    
    /** Estimate total byte count for a list of lines. */
    private long estimateBytes(List<String> lines) {
        long n = 0;
        for (String l : lines) n += l.length() + 1;
        return n;
    }
}
