import os

def clean_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
        
    new_lines = []
    in_fix_block = False
    
    for line in lines:
        stripped = line.strip()
        
            in_fix_block = True
            continue 
            
            in_fix_block = False
            continue 
            
        if in_fix_block:
            if filepath.endswith('.java') and stripped.startswith('//'):
                continue
            if filepath.endswith('.py') and stripped.startswith('#'):
                continue
                
        # Also clean some specific known commented-out lines outside blocks
        if filepath.endswith('.java'):
            if stripped == "// private final HashSet<String> nodesList = new HashSet<>();": continue
            if stripped == "// private volatile boolean voted;": continue
            if stripped == "// this.voted = false;": continue
            if stripped == "// this.term = 0;": continue
            if stripped == "// List coordinators = new ArrayList<>(nodesList);": continue
            if stripped == "// public boolean apply(@RequestParam String id) {": continue
            if stripped == "// String targetUrl = \"http://\" + node + \"/llm\";": continue
            if stripped == "// String targetUrl = \"http://localhost:\" + node + \"/llm/decompose\";": continue
            if stripped == "// String targetUrl = \"http://localhost:\" + node + \"/recipes/search\";": continue
            if stripped == "// String targetUrl = \"http://localhost:\" + node + \"/llm/answer\";": continue
            if stripped == "// private final HashSet<String> nodesList;": continue
            if stripped == "// this.nodesList = new HashSet<>();": continue
            if stripped == "// return requestStatus.keySet();": continue
            if stripped == "// String targetUrl = \"http://localhost:\" + node + \"/copy\";": continue
            if stripped == "// this.nodesList.clear();": continue
            if stripped == "// requestStatus.values().removeIf(request -> request.getTtl().isBefore(now));": continue
        
        new_lines.append(line)
        
    with open(filepath, 'w', encoding='utf-8') as f:
        f.writelines(new_lines)

for root, dirs, files in os.walk('e:/DS/my-spring-project'):
    for file in files:
        if file.endswith('.java') or file.endswith('.py'):
            clean_file(os.path.join(root, file))

print("Cleanup complete.")
