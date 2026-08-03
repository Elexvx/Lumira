import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import ts from 'typescript';

const root = process.cwd();
const sourceRoot = path.join(root, 'src');

const walk = (directory) => fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
  const absolute = path.join(directory, entry.name);
  if (entry.isDirectory()) {
    if (entry.name.startsWith('.') || entry.name === 'node_modules') {
      return [];
    }
    return walk(absolute);
  }
  return /\.(?:ts|tsx)$/u.test(entry.name) ? [absolute] : [];
});

const sourceFiles = walk(sourceRoot);
const violations = [];
const legacyCallKinds = new Map();
const hardcodedMessageCandidates = [];
const referencedDatabaseKeys = new Map();
let legacyCallCount = 0;

const isDatabaseBackedMessageArgument = (node) => {
  let current = node.parent;
  while (current) {
    if (ts.isCallExpression(current)) {
      const expressionName = ts.isIdentifier(current.expression)
        ? current.expression.text
        : ts.isPropertyAccessExpression(current.expression)
          ? current.expression.name.text
          : '';
      if (['databaseMessage', 'formatMessage', 'resolveBuiltinMessage', 't'].includes(expressionName)) {
        return true;
      }
    }
    if (ts.isStatement(current) || ts.isJsxElement(current) || ts.isJsxSelfClosingElement(current)) {
      return false;
    }
    current = current.parent;
  }
  return false;
};

const hasLegacyTranslator = (sourceFile) => sourceFile.statements.some((statement) => {
  if (!ts.isVariableStatement(statement)) {
    return false;
  }
  return statement.declarationList.declarations.some((declaration) =>
    ts.isIdentifier(declaration.name)
    && declaration.name.text === 't'
    && declaration.initializer
    && (ts.isArrowFunction(declaration.initializer) || ts.isFunctionExpression(declaration.initializer))
    && declaration.initializer.parameters.length >= 2
    && ts.isIdentifier(declaration.initializer.parameters[0].name)
    && /^(?:zh|cn|chinese)$/iu.test(declaration.initializer.parameters[0].name.text)
    && ts.isIdentifier(declaration.initializer.parameters[1].name)
    && /^(?:en|english)$/iu.test(declaration.initializer.parameters[1].name.text));
});

for (const absolute of sourceFiles) {
  const relative = path.relative(root, absolute).replaceAll('\\', '/');
  const isTestFile = /\.(?:test|spec)\.[jt]sx?$/u.test(relative);
  const source = fs.readFileSync(absolute, 'utf8');
  const sourceFile = ts.createSourceFile(
    absolute,
    source,
    ts.ScriptTarget.Latest,
    true,
    absolute.endsWith('x') ? ts.ScriptKind.TSX : ts.ScriptKind.TS,
  );
  const legacyTranslator = hasLegacyTranslator(sourceFile);
  let hardcodedCount = 0;

  if (/from\s+['"]@\/locales(?:\/|['"])/u.test(source)) {
    violations.push(`${relative}: imports a static locale catalog`);
  }

  const visit = (node) => {
    if (
      legacyTranslator
      && ts.isCallExpression(node)
      && ts.isIdentifier(node.expression)
      && node.expression.text === 't'
      && node.arguments.length >= 2
    ) {
      legacyCallCount += 1;
      const kind = node.arguments.slice(0, 2).map((argument) => ts.SyntaxKind[argument.kind]).join(' + ');
      legacyCallKinds.set(kind, (legacyCallKinds.get(kind) || 0) + 1);
    }
    const candidateText = ts.isStringLiteralLike(node) || ts.isNoSubstitutionTemplateLiteral(node)
      ? node.text
      : ts.isJsxText(node)
        ? node.text.trim()
        : '';
    if (/\p{Script=Han}/u.test(candidateText) && !isDatabaseBackedMessageArgument(node)) {
      hardcodedCount += 1;
    }
    if (!isTestFile && ts.isCallExpression(node)) {
      const expressionName = ts.isIdentifier(node.expression)
        ? node.expression.text
        : ts.isPropertyAccessExpression(node.expression)
          ? node.expression.name.text
          : '';
      let messageKey = '';
      if (['databaseMessage', 'resolveBuiltinMessage', 't'].includes(expressionName)) {
        const firstArgument = node.arguments[0];
        messageKey = firstArgument && ts.isStringLiteralLike(firstArgument) ? firstArgument.text : '';
      } else if (expressionName === 'formatMessage') {
        const descriptor = node.arguments[0];
        if (descriptor && ts.isObjectLiteralExpression(descriptor)) {
          const idProperty = descriptor.properties.find((property) =>
            ts.isPropertyAssignment(property)
            && ((ts.isIdentifier(property.name) && property.name.text === 'id')
              || (ts.isStringLiteralLike(property.name) && property.name.text === 'id')));
          messageKey = idProperty
            && ts.isPropertyAssignment(idProperty)
            && ts.isStringLiteralLike(idProperty.initializer)
            ? idProperty.initializer.text
            : '';
        }
      }
      if (messageKey && !referencedDatabaseKeys.has(messageKey)) {
        referencedDatabaseKeys.set(messageKey, relative);
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(sourceFile);
  if (hardcodedCount > 0 && !isTestFile) {
    hardcodedMessageCandidates.push({ file: relative, count: hardcodedCount });
  }
}

const localeFiles = fs.existsSync(path.join(sourceRoot, 'locales'))
  ? walk(path.join(sourceRoot, 'locales')).filter((file) => !/(?:^|[\\/])(?:en-US|zh-CN)\.ts$/u.test(file))
  : [];
const populatedLocaleFiles = localeFiles.filter((file) => /['"][\w.-]+['"]\s*:/u.test(fs.readFileSync(file, 'utf8')));
const databaseCatalogPath = path.resolve(root, '../lumira-backend/services/lumira-localization/src/main/resources/localization/ui-catalog.json');
const catalogKeys = fs.existsSync(databaseCatalogPath)
  ? new Set(JSON.parse(fs.readFileSync(databaseCatalogPath, 'utf8')).entries.map((entry) => entry.messageKey))
  : new Set();
const missingDatabaseKeys = [...referencedDatabaseKeys.entries()]
  .filter(([messageKey]) => !catalogKeys.has(messageKey))
  .map(([messageKey, file]) => ({ messageKey, file }));
const hardcodedBaselinePath = path.join(root, 'scripts/database-i18n-hardcoded-baseline.json');
const hardcodedBaseline = fs.existsSync(hardcodedBaselinePath)
  ? JSON.parse(fs.readFileSync(hardcodedBaselinePath, 'utf8'))
  : {};
const hardcodedRegressions = hardcodedMessageCandidates
  .filter((item) => item.count > (hardcodedBaseline[item.file] || 0))
  .map((item) => ({ ...item, baseline: hardcodedBaseline[item.file] || 0 }));

const report = {
  legacyCallCount,
  legacyCallKinds: Object.fromEntries([...legacyCallKinds.entries()].sort((left, right) => right[1] - left[1])),
  populatedLocaleFiles: populatedLocaleFiles.map((file) => path.relative(root, file).replaceAll('\\', '/')),
  violations,
  referencedDatabaseKeyCount: referencedDatabaseKeys.size,
  missingDatabaseKeys,
  hardcodedMessageCandidateCount: hardcodedMessageCandidates.reduce((total, item) => total + item.count, 0),
  hardcodedMessageFiles: hardcodedMessageCandidates.sort((left, right) => right.count - left.count),
  hardcodedRegressions,
};

process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);

if (process.argv.includes('--strict') && (
  legacyCallCount > 0
  || populatedLocaleFiles.length > 0
  || violations.length > 0
  || missingDatabaseKeys.length > 0
  || hardcodedRegressions.length > 0
)) {
  process.exitCode = 1;
}

if (process.argv.includes('--strict-hardcoded') && hardcodedMessageCandidates.length > 0) {
  process.exitCode = 1;
}
